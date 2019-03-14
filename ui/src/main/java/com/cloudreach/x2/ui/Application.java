/*
********************************************************************************
* Copyright (c) 9th November 2018 Cloudreach Limited Europe
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* This Source Code may also be made available under the following Secondary
* Licenses when the conditions for such availability set forth in the Eclipse
* Public License, v. 2.0 are satisfied: GNU General Public License, version 2
* with the GNU Classpath Exception which is
* available at https://www.gnu.org/software/classpath/license.html.
*
* SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
********************************************************************************/
package com.cloudreach.x2.ui;

import com.cloudreach.connect.x2.api.Module;
import com.cloudreach.connect.x2.internal.model.CCError;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.x2.ui.uam.UserAccessManagementSPI;
import com.cloudreach.x2.ui.uam.feature.UserManagementFeature;
import com.cloudreach.x2.ui.uam.spi.JemoSPI;
import com.cloudreach.x2.ui.util.Util;
import com.cloudreach.x2.ui.util.i18n;
import com.cloudreach.x2.ui.view.ButtonView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

/**
 * this will define the application component, which is where a UI application begins.
 * 
 * the application component also defines an entry point which means that it is responsible for 
 * providing things like the anchor URL for the application, the authentication scheme and other
 * the basis for the build of the single page application which results.
 * 
 * The application is a component which will return HTML as an output
 * 
 * @author christopher stura
 */
public abstract class Application extends Component implements Module {

	public static final String MAIN_APPLICATION_AREA = "feature-body";
	public static final String TOP_APPLICATION_MENU = "application-menu";
	public static final String USER_APPLICATION_MENU = "user-app-menu";
	public static final String USER_PROFILE_IMAGE = "user_profile_image";
	private static final String X2_USER_COOKIE = "X2-User-Data-X1";
	
	private Map<Long,UserData> userDataMap = new HashMap<>();
	private boolean authentication = false;
	private boolean userAccessManagement = false;
	private static Logger log = null;
	private String name = null;
	private int id = 0;
	private double version = 0;
	private String title = "Untitled Application";
	private String appKey = "X2";
	private transient Map<String,String> configuration = new HashMap<>();
	private Logger auditLog = null;
	private transient UserAccessManagementSPI uamSPI;
	
	@Override
	public void construct(Logger logger, String name, int id, double version) {
		this.log = logger;
		this.name = name;
		this.id = id;
		this.version = version;
	}
	
	@Override
	public void configure(Map<String, String> configuration) {
		synchronized(this) {
			this.configuration.clear();
			this.configuration.putAll(configuration);
		}
		if(uamSPI == null) {
			uamSPI = new JemoSPI(this);
		}
		Module.super.configure(configuration);
	}
	
	/**
	 * this method will return a reference to the currently registered spi implementation of the user
	 * access management system.
	 * 
	 * @return a reference to the current spi implementation for the user access management sub system.
	 */
	public UserAccessManagementSPI getUserAccessManagement() {
		return this.uamSPI;
	}
	
	/**
	 * through this method you can set your own custom user access management spi implementation.
	 * 
	 * Note: the default implementation user the Eclipse Jemo cloud native runtime and will run on all public cloud providers.
	 * 
	 * @param spiImplementation a reference to your implementation of the user access management data interface
	 */
	public void setUserAccessManagementSPI(UserAccessManagementSPI spiImplementation) {
		this.uamSPI = spiImplementation;
	}

	@Override
	public double getVersion() { return version; };
	
	public int getApplicationId() { return this.id; };

	@Override
	public CCMessage process(CCMessage message) throws Throwable { return null; }

	@Override
	public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
		//we will return the shell html for the application.
		response.setCharacterEncoding("UTF-8");
		
		//the behaviour will actually depend on the path that is called.
		final String basePath = request.getServletPath();
		final String appPath = basePath.startsWith(getFullBasePath()) ? basePath.substring(getFullBasePath().length()) : basePath;
		
		String userJsonHeader = request.getHeader("x2.user");
		Cookie userDataCookie = Arrays.asList(request.getCookies() == null ? new Cookie[] {} : request.getCookies()).stream().filter(c -> c.getName().equals(X2_USER_COOKIE))
			.findFirst().orElse(new Cookie(X2_USER_COOKIE, Util.base64(UUID.randomUUID().toString().getBytes("UTF-8"))));
		
		userDataCookie.setHttpOnly(true);
		userDataCookie.setSecure(true);
		userDataCookie.setPath("/");
		userDataCookie.setMaxAge(-1);
		if(userJsonHeader != null) {
			this.userDataMap.put(Thread.currentThread().getId(),Util.fromJSON(UserData.class, userJsonHeader));
		}
		if(!userDataCookie.getValue().trim().isEmpty()) {
			//lets get the serialized cookie data from S3.
			UserData userData = getRuntime().retrieve("x2msgpayload", new String(Util.base64(userDataCookie.getValue()), "UTF-8"), UserData.class);
			if(userData != null) {
				this.userDataMap.put(Thread.currentThread().getId(),userData);
			} else {
				userDataCookie.setValue(Util.base64(UUID.randomUUID().toString().getBytes("UTF-8")));
			}
		}
		
		try (OutputStream responseOutput = response.getOutputStream()) {
			if(appPath.isEmpty() || appPath.equals("/")) {
				response.setContentType("text/html");
				//we should remove the cookie the first time we draw this.
				userDataCookie.setMaxAge(0);
				userDataCookie.setValue(Util.base64(UUID.randomUUID().toString().getBytes("UTF-8"))); //set the value to something random so even if it is not deleted
				response.addCookie(userDataCookie);
				
				OutputStreamWriter out = new OutputStreamWriter(responseOutput, "UTF-8");
				out.write("<!DOCTYPE html>");
				out.write("<html>");
				out.write("<head>");
				out.write("<meta charset=\"utf-8\">");
				out.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
				out.write("<title>"+getTitle()+"</title>");
				out.write("<link href=\""+getFullBasePath()+"/css/bootstrap.min.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/font-awesome/css/font-awesome.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/footable/footable.core.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/animate.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/style.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/codemirror.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/js/addon/hint/show-hint.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/touchspin/jquery.bootstrap-touchspin.min.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/touchspin/jquery.bootstrap-touchspin.min.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/jQueryUI/jquery-ui.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/jQueryUI/jquery-ui-1.10.4.custom.min.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/jasny/jasny-bootstrap.min.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/colorpicker/bootstrap-colorpicker.min.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/summernote/summernote.css\" rel=\"stylesheet\">");
				out.write("<link href=\""+getFullBasePath()+"/css/plugins/summernote/summernote-bs3.css\" rel=\"stylesheet\">");
				out.write("</head>");
				out.write("<body cz-shortcut-listen=\"true\">");
				out.write("<div id=\"wrapper\">");
				out.write("<nav class=\"navbar-default navbar-static-side\" role=\"navigation\">");
				out.write("<div class=\"sidebar-collapse\">");
				out.write("<ul class=\"nav metismenu\" id=\"side-menu\">");
				out.write("</ul>");
				//the definition of the menu would go here (we would build this in javascript and not statically here
				out.write("</div>");
				out.write("</nav>");
				out.write("<div id=\"page-wrapper\" class=\"gray-bg\">");
				out.write("<div class=\"row border-bottom\">");
				out.write("<nav class=\"navbar navbar-static-top\" role=\"navigation\" style=\"margin-bottom: 0\">");
				out.write("<div class=\"navbar-header\">");
				out.write("<a class=\"navbar-minimalize minimalize-styl-2 btn btn-primary \" href=\"#\"><i class=\"fa fa-bars\"></i></a>");
				//other navigation bar header components would be defined in another json component and would then be drawn dynamically.
				out.write("</div>");
				out.write("<ul class=\"nav navbar-top-links navbar-right\" id=\""+TOP_APPLICATION_MENU+"\">");
				out.write("<li><a class=\"m-r-sm text-muted welcome-message\">"+getTitle()+"</a></li>");
				//we need an event which will populate the top menu in the application if this is available and implemented.
				//TODO: make sure a container exists for this.
				out.write("</ul>");
				out.write("</nav>");
				out.write("</div>");
				out.write("<div class=\"col-lg-12 m-t-sm\" id=\"notification-area\"></div>");
				//let me add an area to contain the title of the page
				out.write("<div class=\"row wrapper border-bottom white-bg page-heading\" id=\"feature-title\"></div>");
				out.write("<div id=\"feature-body\" class=\"wrapper wrapper-content animated fadeInRight\"></div>");
				//all of the body work would come out of this based on where we are in the navigation.
				out.write("<div class=\"footer\">");
				//the status bar component would be drawn out here (again extracted in JSON format from a component)
				out.write("</div>");
				out.write("</div>");
				out.write("</div>");
				out.write("<script src=\""+getFullBasePath()+"/js/jquery-2.1.1.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/bootstrap.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/metisMenu/jquery.metisMenu.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/slimscroll/jquery.slimscroll.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/footable/footable.all.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/inspinia.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/pace/pace.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/codemirror.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/mode/sql/sql.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/mode/javascript/javascript.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/addon/hint/show-hint.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/addon/hint/sql-hint.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/addon/hint/javascript-hint.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/touchspin/jquery.bootstrap-touchspin.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/jquery-ui/jquery-ui.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/jasny/jasny-bootstrap.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/colorpicker/bootstrap-colorpicker.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/js/plugins/summernote/summernote.min.js\"></script>");
				out.write("<script src=\""+getFullBasePath()+"/ckeditor/ckeditor.js\"></script>");
				if(isAuthentication()) {
					out.write("<script src=\"//js.live.net/v5.0/wl.js\"></script>");
					out.write("<script src=\"https://apis.google.com/js/platform.js\" async defer></script>");
					out.write("<script>window.x2_authentication = true;</script>");
				}
				out.write("<script>window.x2_app = { key: \""+getAppKey()+"\", title: \""+getTitle()+"\", microsoft_key: \""+getMicrosoftApplicationKey()+"\" "
					+(!Util.S(getGoogleOAuthClientId()).isEmpty() ? ",google_client_id: \""+getGoogleOAuthClientId()+"\",google_api_key: \""+getGoogleAPIKey()+"\"" : "")+" }; </script>");
				out.write("<script type=\"text/javascript\" src=\""+getFullBasePath()+"/js/x2ui.js\"></script>");
				if(isAuthentication()) {
					if(!Util.S(getGoogleOAuthClientId()).isEmpty()) {
						out.write("<script async defer src=\"https://apis.google.com/js/platform.js\" onload=\"gapi.load('client:auth2', CX2.auth.google.init);\" onreadystatechange=\"if (this.readyState === 'complete') this.onload()\"></script>");
					}
				}
				out.write("</body>");
				out.write("</html>");
				out.flush();
			} else if(appPath.startsWith("/events/")) {
				String event = appPath.substring(appPath.lastIndexOf('/')+1);
				try {
					Object retval = processEvent(appPath,event, request);
					if(getAuthenticatedUser() != null) {
						getRuntime().store("x2msgpayload", new String(Util.base64(userDataCookie.getValue()), "UTF-8"), getAuthenticatedUser());
					}
					response.addCookie(userDataCookie);
					if(retval != null) {
						responseOutput.write(Util.toJSON(retval).getBytes("UTF-8"));
						responseOutput.flush();
					} else {
						response.sendError(404, "Event ["+event+"] is undefined or un-implemented");
					}
				}catch(Throwable ex) {
					if(ex instanceof ApplicationException) {
						response.setStatus(400);
						Util.stream(responseOutput, new ByteArrayInputStream(Util.toJSON(ex).getBytes("UTF-8")));
						responseOutput.flush();
					} else {
						response.setStatus(500);
						Util.stream(responseOutput, new ByteArrayInputStream(("Event ["+event+"] returned the error: "+CCError.toString(ex)).getBytes("UTF-8")));
						responseOutput.flush();
					}
				}
			} else if(appPath.startsWith("/authentication/")) {
				if(request.getParameter("code") != null && request.getParameter("redirect_uri") != null) {
					HttpClient httpClient = HttpClientBuilder.create().build();
					HttpPost postRequest = new HttpPost("https://login.microsoftonline.com/common/oauth2/v2.0/token");
					List<BasicNameValuePair> urlParameters = new ArrayList<>();
					urlParameters.add(new BasicNameValuePair("client_secret", getMicrosoftApplicationPassword()));
					urlParameters.add(new BasicNameValuePair("client_id",getMicrosoftApplicationKey()));
					urlParameters.add(new BasicNameValuePair("code", request.getParameter("code")));
					urlParameters.add(new BasicNameValuePair("redirect_uri", request.getParameter("redirect_uri")));
					urlParameters.add(new BasicNameValuePair("grant_type", "authorization_code"));
					postRequest.setEntity(new UrlEncodedFormEntity(urlParameters));
					HttpResponse authResponse = httpClient.execute(postRequest);

					HttpEntity entity = authResponse.getEntity();
					response.setContentType(entity.getContentType().getValue());
					response.setContentLengthLong(entity.getContentLength());
					entity.writeTo(responseOutput);
				} else if(appPath.indexOf("profile_picture") != -1) {
					if(!onUserProfileImage(request, response, responseOutput)) {
						HttpClient httpClient = HttpClientBuilder.create().build();
						HttpGet getRequest = new HttpGet("https://graph.microsoft.com/v1.0/me/photo/$value");
						getRequest.addHeader("Authorization", "Bearer "+request.getParameter("token"));
						getRequest.setConfig(RequestConfig.custom().setConnectTimeout(500).setConnectionRequestTimeout(2500).build()); //if microsoft graph does not respond in 3 seconds we revert

						try {
							HttpResponse imgResponse = httpClient.execute(getRequest);
							HttpEntity entity = imgResponse.getEntity();
							if(imgResponse.getStatusLine().getStatusCode() == 200) {
								response.setContentType(entity.getContentType().getValue());
								response.setContentLengthLong(entity.getContentLength());
								entity.writeTo(responseOutput);
							} else {
								response.setContentType("image/png");
								InputStream resIn = Application.class.getResourceAsStream("/com/cloudreach/x2/ui/user_icon.png");
								Util.stream(responseOutput, resIn);
							}
						}catch(Throwable ex) {
							response.setContentType("image/png");
							InputStream resIn = Application.class.getResourceAsStream("/com/cloudreach/x2/ui/user_icon.png");
							Util.stream(responseOutput, resIn);
						}
					}
				} else {
					response.setStatus(400);
				}
			} else {
				if(!processHttpRequest(appPath.startsWith("/") ? appPath : "/"+appPath, response, request, responseOutput)) {
					if(appPath.endsWith(".js")) {
						response.setContentType("text/javascript");
					} else if(appPath.endsWith(".css")) {
						response.setContentType("text/css");
					} else if(appPath.endsWith(".woff2")) {
						response.setContentType("font/woff2");
					}
					//we are looking to download a file directly embedded in our resources package.
					InputStream resIn = Application.class.getResourceAsStream("/com/cloudreach/x2/ui/"+(appPath.startsWith("/") ? appPath.substring(1) : appPath));
					if(resIn != null) {
						Util.stream(responseOutput, resIn);
					} else {
						log.log(Level.WARNING,"The resource: /com/cloudreach/x2/ui/%s could not be found",(appPath.startsWith("/") ? appPath.substring(1) : appPath));
						response.sendError(404,"The resource: /com/cloudreach/x2/ui/"+(appPath.startsWith("/") ? appPath.substring(1) : appPath)+" could not be found");
					}
				}
			}
			responseOutput.flush();
		} finally {
			userDataMap.remove(Thread.currentThread().getId());
		}
	}
	
	public String getMicrosoftApplicationKey() {
		return null;
	}
	
	public String getMicrosoftApplicationPassword() {
		return null;
	}

	@Override
	public void stop() {}

	@Override
	public void start() {}

	@Override
	public void upgraded() {}

	@Override
	public void installed() {}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAppKey() {
		return appKey;
	}

	public void setAppKey(String appKey) {
		this.appKey = appKey;
	}
	
	public String getFullBasePath() {
		return "/"+id+"/v"+String.valueOf(version)+(!getBasePath().startsWith("/") ? "/" : "")+getBasePath();
	}
	
	public abstract List<ApplicationFeature> onListFeatures() throws Throwable;

	public UserData onUserLogin(UserData user) throws Throwable { return user; }
	
	/**
	 * this method can be used to produce a top right menu for your application which can contain multiple different menu items
	 * each high level feature will call a custom action to draw it's content internally and top menu will display sub menu items as
	 * the content directly beneath them if they are present. however this additional information will only be present when they are clicked
	 * and will be reloaded whenever they have their top level feature clicked.
	 * 
	 * the two components of the top menu "messages" and "notifications" will instead be polled at regular intervals on the server for
	 * changes.
	 * 
	 * @return a reference to the top menu descriptor.
	 * @throws Throwable if there was a problem generating the menu. By default no top menu is provided
	 */
	protected TopMenu onTopMenu() throws Throwable { return new TopMenu().withShowLogout(isAuthentication()).withTitle(getTitle()); }
	protected UserMenu onUserMenu() throws Throwable { return new UserMenu(); }
	
	/**
	 * you should override this method if you want to add custom processing for user profile management.
	 * 
	 * @param request
	 * @param response
	 * @param responseOutput
	 * @return 
	 */
	protected boolean onUserProfileImage(HttpServletRequest request, HttpServletResponse response, OutputStream responseOutput) throws IOException { return false; }
	
	public Logger getApplicationFeatureAuditLogger() throws Throwable {
		if(auditLog == null) {
			auditLog = Logger.getLogger(id+":"+version+":"+getClass().getSimpleName()+"_AUDIT");
			Class cc = Class.forName("com.cloudreach.connect.x2.CC");
			auditLog.setParent((Logger)cc.getField("sysLogger").get(cc));
		}
		return auditLog;
	}
	
	private Object processEvent(String appPath,String event,HttpServletRequest request) throws Throwable {
		//we are going to re-route this event to the backend component and ask that the necessary objects be generated so we can then return them to the front-end.
		if(event.equals("onListFeatures")) {
			List<ApplicationFeature> featureList = onListFeatures();
			if(isUserAccessManagement()) {
				//we need to look for an anchor point.
				List<ApplicationFeature> uamAnchors = featureList.stream().filter(f -> f.isAnchorUserAccessManagement()).collect(Collectors.toList());
				if(uamAnchors.isEmpty()) {
					//we need to add a new top level feature to host uam
					ApplicationFeature uamFeature = new ApplicationFeature(ICON.tree, i18n.USER_ACCESS_MANAGEMENT.getString("app.defaultcontainer.title"));
					uamFeature.setAnchorUserAccessManagement(true);
					featureList.add(0, uamFeature);
					uamAnchors.add(uamFeature);
				} 
				
				//we need to add the application suite to all of the defined anchor points.
				uamAnchors.forEach(f -> f.getSubfeatures().addAll(Arrays.asList(UserManagementFeature.FEATURE)));
			}
			return featureList;
		} else if(event.equals("onUserLogin")) {
			if(getAuthenticatedUser() != null) {
				return onUserLogin(getAuthenticatedUser());
			}
			throw new ApplicationException("Application Authentication was Invalid");
		} else if(event.equals("onTopMenu")) {
			return onTopMenu();
		} else if(event.equals("onUserMenu")) {
			if(getAuthenticatedUser() != null) {
				return onUserMenu();
			}
			throw new ApplicationException("Application Authentication was Invalid");
		} else if(appPath.contains("/feature/")) {
			//this is a feature definition so we need to try and load and create an instance of this class which we will then call to generate the view definition.
			Class cls = Class.forName(event);
			if(Panel.class.isAssignableFrom(cls)) {
				Panel p = Panel.class.cast(cls.newInstance());
				p.setApplication(this);
				String payload = Util.toString(request.getInputStream());
				ApplicationFeature f = (payload == null || payload.isEmpty() ? null : Util.fromJSON(ApplicationFeature.class, payload));
				//the scope is to create an audit log. to do this we will assume the feature is the service which will be set in the user's session scope here.
				if(getAuthenticatedUser() != null) {
					getAuthenticatedUser().getData().put("CURRENT_FEATURE", Util.toJSON(f));
				}
				return p.onCreate(f);
			} else {
				log.log(Level.WARNING,"The Feature defined by class [%s] could not be executed because the implementation does not derive from a Panel",event);
			}
		} else if(appPath.contains("/action/")) {
			Class cls = Class.forName(event);
			if(Button.class.isAssignableFrom(cls)) {
				Button b = Button.class.cast(cls.newInstance());
				b.setApplication(this);
				String payload = null;
				if(request.getContentType().equals("application/json")) {
					payload = Util.toString(request.getInputStream());
				} else {
					payload = Util.toString(request.getPart("cx2.sys.view").getInputStream());
					//we should also add all of the parts associated to this request which will contain all of the fields which have been submitted in the form.
					for(Part p : request.getParts()) {
						b.addFormPart(p.getName(), p);
					}
				}
				//next every time a button is pressed we should write a log entry using our log service through the runtime component.
				final ButtonView bv = Util.fromJSON(ButtonView.class, payload);
				boolean succeeded = true;
				long start = System.currentTimeMillis();
				long end = 0;
				try {
					return b.onClick(bv);
				}catch(Throwable ex) {
					succeeded = false;
					end = System.currentTimeMillis();
					throw ex;
				}finally {
					if(end == 0) {
						end = System.currentTimeMillis();
					}
					if(getAuthenticatedUser() != null && getAuthenticatedUser().getData().containsKey("CURRENT_FEATURE")) {
						ApplicationFeature feature = Util.fromJSON(ApplicationFeature.class, getAuthenticatedUser().getData().get("CURRENT_FEATURE").toString());
						getApplicationFeatureAuditLogger().info(String.format("[%s] The User %s %s <%s> pressed the button %s%s the following data was provided as part of the request %s. The operation %s and took %d (milliseconds)",feature.getTitle(), getAuthenticatedUser().getFirstName(), 
							getAuthenticatedUser().getLastName(), getAuthenticatedUser().getUsername(), bv.getTitle(), bv.getLogName() == null ? "" : " Application Context ["+bv.getLogName()+"]",
							b.listFormFields().stream()
								.map(p -> {
									Part part = b.getFormPart(p);
									return String.format("PARAMETER: [name: %s, value: %s, size: %d]",
										part.getName(), part.getSubmittedFileName() != null ? part.getSubmittedFileName() : b.getFormFieldAsString(p), part.getSize());
								}).collect(Collectors.joining(",")),
							succeeded ? "SUCCEEDED" : "FAILED", end-start
						));
					}
				}
			} else if(Component.class.isAssignableFrom(cls)) {
				Component comp = Component.class.cast(cls.newInstance());
				comp.setApplication(this);
				int actionPos = appPath.indexOf("/action/")+"/action/".length();
				String eventType = appPath.substring(actionPos, appPath.indexOf("/", actionPos));
				String payload = null;
				HashMap<String,Object> parameters = new HashMap<>();
				request.getParameterMap().keySet().stream().forEach((qp) -> {
					parameters.put(qp,request.getParameter(qp));
				});
				if(request.getContentType().equals("application/json")) {
					payload = Util.toString(request.getInputStream());
				} else {
					payload = Util.toString(request.getPart("cx2.sys.view").getInputStream());
					//we should also add all of the parts associated to this request which will contain all of the fields which have been submitted in the form.
					for(Part p : request.getParts()) {
						if(p.getSubmittedFileName() != null) {
							parameters.put(p.getName(), p);
						} else {
							parameters.put(p.getName(), Util.toString(p.getInputStream()));
						}
					}
				}
				return comp.handleEvent(eventType, parameters, payload);
			} else {
				log.log(Level.WARNING,"The Action defined by class [%s] could not be executed because the implementation does not derive from a Button",event);
			}
		} else {
			log.log(Level.WARNING,"[%s] The Event %s could not be processed as it has not been defined for this component",new Object[] { appPath,event });
		}
		
		return null;
	}

	protected Map<String, String> getConfiguration() {
		return configuration;
	}
	
	public static void moduleLog(Level level,String message,String... parameters) {
		log.log(level, message, parameters);
	}
	
	public boolean processHttpRequest(String contextPath, HttpServletResponse response, HttpServletRequest request, OutputStream output) throws Throwable {
		return false;
	}

	public boolean isAuthentication() {
		return authentication;
	}

	public void setAuthentication(boolean authentication) {
		this.authentication = authentication;
	}
	
	public UserData getAuthenticatedUser() {
		return userDataMap.get(Thread.currentThread().getId());
	}
	
	public String getGoogleOAuthClientId() {
		return null;
	}
	
	public String getGoogleOAuthClientSecret() {
		return null;
	}
	
	public String getGoogleAPIKey() {
		return null;
	}

	/**
	 * this method will return true if the default user access management system is enabled for this application
	 * 
	 * @return true if embedded user access management is enabled 
	 */
	public boolean isUserAccessManagement() {
		return userAccessManagement;
	}

	/**
	 * this method will allow you to enable or disable the embedded user access management system for your application.
	 * User access management will allow you to manage users, groups, user metadata and application level security.
	 * 
	 * Only set this to true if you want to add the features to your application automatically. You can always add the applications
	 * you want manually by using the specific features already provided by the framework.
	 * 
	 * @param userAccessManagement true to enable the default user access management system by default user access management is disabled.
	 */
	public void setUserAccessManagement(boolean userAccessManagement) {
		this.userAccessManagement = userAccessManagement;
	}
}
