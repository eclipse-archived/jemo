/* 
 * This javascript file contains the glue needed to build an x2ui application.
 * it will effectively bind restful backend components with a single page user interface
 * which is defined in an event driven strategy on the backend.
 */

if (!String.prototype.startsWith) {
    String.prototype.startsWith = function(searchString, position){
      position = position || 0;
      return this.substr(position, searchString.length) === searchString;
  };
}

if (!String.prototype.endsWith) {
  String.prototype.endsWith = function(searchString, position) {
      var subjectString = this.toString();
      if (typeof position !== 'number' || !isFinite(position) || Math.floor(position) !== position || position > subjectString.length) {
        position = subjectString.length;
      }
      position -= searchString.length;
      var lastIndex = subjectString.lastIndexOf(searchString, position);
      return lastIndex !== -1 && lastIndex === position;
  };
}

// https://tc39.github.io/ecma262/#sec-array.prototype.includes
if (!Array.prototype.includes) {
  Object.defineProperty(Array.prototype, 'includes', {
    value: function(searchElement, fromIndex) {

      // 1. Let O be ? ToObject(this value).
      if (this == null) {
        throw new TypeError('"this" is null or not defined');
      }

      var o = Object(this);

      // 2. Let len be ? ToLength(? Get(O, "length")).
      var len = o.length >>> 0;

      // 3. If len is 0, return false.
      if (len === 0) {
        return false;
      }

      // 4. Let n be ? ToInteger(fromIndex).
      //    (If fromIndex is undefined, this step produces the value 0.)
      var n = fromIndex | 0;

      // 5. If n ≥ 0, then
      //  a. Let k be n.
      // 6. Else n < 0,
      //  a. Let k be len + n.
      //  b. If k < 0, let k be 0.
      var k = Math.max(n >= 0 ? n : len - Math.abs(n), 0);

      // 7. Repeat, while k < len
      while (k < len) {
        // a. Let elementK be the result of ? Get(O, ! ToString(k)).
        // b. If SameValueZero(searchElement, elementK) is true, return true.
        // c. Increase k by 1.
        // NOTE: === provides the correct "SameValueZero" comparison needed here.
        if (o[k] === searchElement) {
          return true;
        }
        k++;
      }

      // 8. Return false
      return false;
    }
  });
}

window.downloadFile = function(sUrl) {
	//Creating new link node.
	var link = document.createElement('a');
	link.style.display = "none";
	link.href = sUrl+(sUrl.indexOf('?') > 0 ? '&' : '?')+'download&uuid='+encodeURIComponent(CX2.util.UUID());

	if (link.download !== undefined){
			//Set HTML5 download attribute. This will prevent file from opening if supported.
			var fileName = sUrl.substring(sUrl.lastIndexOf('/') + 1, sUrl.length);
			link.download = fileName;
	}
	document.body.appendChild(link);
	$(link)[0].click();
};
 
window.downloadFile.isChrome = navigator.userAgent.toLowerCase().indexOf('chrome') > -1;
window.downloadFile.isSafari = navigator.userAgent.toLowerCase().indexOf('safari') > -1;

var CX2 = {
	activeDialog: null,
	activeButton: null
};
CX2.getError = function(key) {
	if(CX2.LAST_ERROR) {
		for(var i = 0; i < CX2.LAST_ERROR.errors.length; i++) {
			var error = CX2.LAST_ERROR.errors[i];
			if(error.key === key) {
				return error;
			}
		}
	}
	
	return null;
};
CX2.runOnBackend = function(eventPath,success,error,context,backendData,contentType) {
	$.ajax({
		cache: false,
		contentType: contentType === null ? "application/json" : contentType,
		dataType: "json",
		method: "POST",
		processData: false,
		context: context,
		data: backendData,
		url: (document.location.pathname === "/" ? "" : document.location.pathname)+"/events/"+eventPath,
		error: error,
		success: success,
		beforeSend: function(xhr) { 
			if(CX2.user_token) {
				xhr.setRequestHeader("x2.user", JSON.stringify(CX2.user));
			}
		}
	});
	
	return false;
};
CX2.clearNotifications = function() {
	var area = document.getElementById("notification-area");
	area.innerHTML = ""; //remove any existing notifications.
	CX2.LAST_ERROR = null;
	$(".form-group").removeClass("has-error");
	$("label").find("small").remove(".x2_error_message"); //remove any errors that are no longer relevant
	$("label").find("br").remove(".x2_error_message"); //remove any errors that are no longer relevant
};

CX2.enableActiveButton = function() {
	if(CX2.activeButton) {
		$(CX2.activeButton).removeClass("disabled");
		CX2.activeButton.disabled = false;
		CX2.activeButton = null;
	}
};

CX2.handleBackendError = function(xhr,status,error) {
	CX2.enableActiveButton();
	var area = document.getElementById("notification-area");
	CX2.clearNotifications();
	var msg = document.createElement("div");
	msg.className = "alert alert-danger alert-dismissable";
	var msgDetail = error;
	if(error === "Server Error") {
		msgDetail = xhr.responseText;
	} else if(error === "Bad Request") {
		var msgObj = JSON.parse(xhr.responseText);
		msgDetail = msgObj.message;
		CX2.LAST_ERROR = msgObj;
		for(var i = 0; i < msgObj.errors.length; i++) {
			var formError = msgObj.errors[i];
			var formGroup = document.getElementById("x2_form_group_"+formError.key);
			if(formGroup) {
				$(formGroup).addClass("has-error");
				$(formGroup).find("label").find("small").remove(".x2_error_message");
				$(formGroup).find("label").append("<br class='x2_error_message'><small class='x2_error_message'>"+formError.message+"</small>");
			}
		}
	}
	msg.innerHTML = "<button aria-hidden=\"true\" data-dismiss=\"alert\" class=\"close\" type=\"button\">×</button><b>"+status+"</b><span class=\"m-l-sm\"><pre>"+msgDetail+"</pre></span>";
	area.appendChild(msg);
};
CX2.handleNotification = function(viewDefinition) {
	var area = null;
	if(CX2.activeDialog) {
		area = document.getElementById("notification-area-"+CX2.activeDialog.id);
		if(!area) {
			var dialogBody = $(CX2.activeDialog).find(".modal-body")[0];
			area = document.createElement("div");
			area.className = "col-lg-12 m-t-sm";
			area.id = "notification-area-"+CX2.activeDialog.id;
			$(dialogBody).prepend(area);
		}
	} else {
		area = document.getElementById("notification-area");
	}
	CX2.clearNotifications();
	if(viewDefinition.info_message || viewDefinition.warn_message) {
		var msgText = (viewDefinition.warn_message ? viewDefinition.warn_message : viewDefinition.info_message);
		var msg = document.createElement("div");
		msg.className = "alert alert-"+(viewDefinition.warn_message ? "warning" : (viewDefinition.info_message ? "info" : "success"))+" alert-dismissable";
		msg.innerHTML = "<button aria-hidden=\"true\" data-dismiss=\"alert\" class=\"close\" type=\"button\">×</button><span class=\"m-l-sm\">"+msgText+"</span>";
		area.appendChild(msg);
	}
};
CX2.buildComponents = function(parentComponent,parentElm) {
	if(parentComponent.components) {
		for(var j = 0; j < parentComponent.components.length; j++) {
			var component = parentComponent.components[j];
			CX2.buildComponent(component,parentElm);
		}
	}
};
CX2.buildComponent = function(component,parentElm) {
	var componentElm = null;
	if(component.class === "WindowView") {
		componentElm = CX2.components.window(component);
	} else if(component.class === "TableView") {
		componentElm = CX2.components.table(component);
	} else if(component.class === "ButtonView") {
		componentElm = CX2.components.button(component);
	} else if(component.class === "FormView") {
		componentElm = CX2.components.form(component);
	} else if(component.class === "PicklistView") {
		componentElm = CX2.components.picklist(component);
	} else if(component.class === "TextView") {
		componentElm = CX2.components.text(component);
	} else if(component.class === "CodeView") {
		componentElm = CX2.components.code_editor(component);
	} else if(component.class === "RowView") {
		componentElm = CX2.components.row(component);
	} else if(component.class === "FormSectionView") {
		componentElm = CX2.components.form_section(component);
	} else if(component.class === "AreaView") {
		componentElm = CX2.components.area(component);
	} else if(component.class === "LabelView") {
		componentElm = CX2.components.label(component);
	} else if(component.class === "MultiselectPicklistView") {
		componentElm = CX2.components.multiselect_picklist(component);
	} else if(component.class === "InputButtonView") {
		componentElm = CX2.components.inputbutton(component);
	} else if(component.class === "DataView") {
		componentElm = CX2.components.data_island(component);
	} else if(component.class === "NumberView") {
		componentElm = CX2.components.number(component);
	} else if(component.class === "BooleanView") {
		componentElm = CX2.components.boolean(component);
	} else if(component.class === "DataListView") {
		componentElm = CX2.components.datalist(component);
	} else if(component.class === "TabView") {
		componentElm = CX2.components.tab(component);
	} else if(component.class === "BinaryView") {
		componentElm = CX2.components.binary(component);
	} else if(component.class === "ReadOnlyView") {
		componentElm = CX2.components.readonly(component);
	} else if(component.class === "ColorPickerView") {
		componentElm = CX2.components.colorpicker(component);
	} else if(component.class === "HTMLView") {
		componentElm = CX2.components.htmleditor(component);
	} else if(component.class === "CheckboxGroupView") {
		componentElm = CX2.components.checkboxgroup(component);
	} else if(component.class === "DateView") {
		componentElm = CX2.components.date(component);
	} else if(component.class === "TimeView") {
		componentElm = CX2.components.time(component);
	} else if(component.class === "DateTimeView") {
		componentElm = CX2.components.datetime(component);
	} else if(component.class === "TopMenu") {
		componentElm = CX2.components.top_menu(component);
	} else if(component.class === "EmbeddedWebView") {
		componentElm = CX2.components.embedded_web(component);
	} else if(component.class === "UserMenu") {
		componentElm = CX2.components.user_menu(component);
	} else if(component.class === "ImageView") {
		componentElm = CX2.components.image_view(component);
	} else if(component.class === "AreaFormView") {
		componentElm = CX2.components.areaform(component);
	} else {
		componentElm = document.createElement("div");
		componentElm.innerHTML = "<b>Warning: The view ["+component.class+"] has not yet been implemented</b>";
	}

	if(componentElm) {
		//if handlers are present then we will register an event on the newly created component for each of the handlers.
		if(component.handlers) {
			var valueKeys = Object.keys(component.handlers);
			for(var i = 0; i < valueKeys.length; i++) {
				var eventName = valueKeys[i];
				var handler = component.handlers[eventName];
				$(componentElm).on(eventName,{ view: component, handler: handler },function(event) {
					//here we are going to make a backend call when this event is triggered.
					CX2.invokeHandler({
						target: this,
						data: {
							target: event.data.handler.target,
							formId: event.data.handler.formId,
							description: event.data.view.description,
							action: event.data.handler.handler,
							attributes: event.data.view.attributes
						}
					});
				});
			}
		}
		if(Array.isArray(componentElm)) {
			for(var k = 0; k < componentElm.length; k++) {
				parentElm.appendChild(componentElm[k]);
			}
		} else {
			parentElm.appendChild(componentElm);
		}
	}
};
CX2.runFeature = function(data,status,xhr) {
	CX2.enableActiveButton();
	CX2.handleNotification(data);
	//we need to set the title of the feature and the path.
	var featureTitleElm = document.getElementById("feature-title");
	featureTitleElm.innerHTML = "<div class=\"col-lg-10\"><h2>"+data.title+"</h2></div>";
	
	var featureBodyElm = document.getElementById("feature-body");
	featureBodyElm.innerHTML = ""; //delete all existing contents
	for(var i = 0; i < data.rows.length; i++) {
		var row = data.rows[i];
		featureBodyElm.appendChild(CX2.components.row(row));
	}
	CX2.applyTheme();
};

CX2.runAction = function(data,status,xhr) {
	CX2.sys_runAction(this,data,status,xhr);
}

CX2.sys_runAction = function(targetElm,data,status,xhr) {
	CX2.enableActiveButton();
	if(data.class && data.class !== "DialogView") {
		targetElm.innerHTML = ""; //empty all nodes from the target element.
		if(CX2.activeDialog) {
			//we are actually only going to close the modal if the target defined (this) is not a child of the dialog
			if(!$.contains(CX2.activeDialog,targetElm)) {
				$(CX2.activeDialog).modal('hide');
				CX2.activeDialog = null;
			}
		}
	}
	//we now need to process all of the components using the data as a view target and this as a view target
	//we should check if there are any events which we need to run
	if(data.events && data.events.length) {
		for(var i = 0; i < data.events.length; i++) {
			var event = data.events[i];
			CX2.runEvent(event.event,event.target,event.payload,CX2.runAction);
		}
	} 
	if(data.class === "PanelView") {
		CX2.runFeature(data,status,xhr);
	} else if(data.class === "DialogView") {
		var dialog = CX2.components.dialog(data);
		$(dialog).modal();
		$(dialog).on('hidden.bs.modal',dialog,function() {
			$(this).remove();
		});
		$(dialog).on('shown.bs.modal', function() {
				CX2.applyTheme();
		});
		CX2.activeDialog = dialog;
		CX2.handleNotification(data);
	} else {
		CX2.handleNotification(data);
		if(targetElm.appendChild) {
			CX2.buildComponent(data,targetElm);
			CX2.applyTheme();
		} else {
			console.log(JSON.stringify(targetElm));
		}
	}
	if(targetElm.dialogId) {
		$('#'+targetElm.dialogId).modal('hide');
	}
};

CX2.applyTheme = function() {
	$('.collapse-link:not(.x2_event_bound)').addClass('x2_event_bound').on('click', function () {
			var ibox = $(this).closest('div.ibox');
			var button = $(this).find('i');
			var content = ibox.find('div.ibox-content');
			content.slideToggle(200);
			button.toggleClass('fa-chevron-up').toggleClass('fa-chevron-down');
			ibox.toggleClass('').toggleClass('border-bottom');
			setTimeout(function () {
					ibox.resize();
					ibox.find('[id^=map-]').resize();
			}, 50);
	});
	
	$('.codemirror:not(.x2_initialised)').addClass('x2_initialised').each(function(index,element) {
		window.editor = CodeMirror.fromTextArea(element,{
			mode: element.mode,
			indentWithTabs: true,
			smartIndent: true,
			lineNumbers: true,
			matchBrackets : true,
			autofocus: true,
			extraKeys: {"Ctrl-Space": "autocomplete"}
		});
		window.editor.on('change',function(cm,changeObj) {
			$(cm.getTextArea()).val(cm.getValue());
		});
	});
	
	$('.footable:not(.x2_initialised)').addClass('x2_initialised').footable().bind({
			'footable_row_expanded' : function(e) {
					//now we need to slightly adapt the footable.
					$('.footable-row-detail-inner').css('width','100%');
					$('.footable-row-detail-name').remove();                  
			},
			'footable_redrawn' : function(e) {
				$(this).find(".no-sort").removeClass("footable-sortable");
				$(this).find(".no-sort").find('.footable-sort-indicator').remove();
				$(this).find(".no-sort").unbind('click'); //make sure any footable events are removed
				if(this.actualPagingBlock) {
					$(this).find("tfoot").remove();
					var clonedPagingBlock = this.actualPagingBlock.cloneNode(true);
					this.appendChild(this.actualPagingBlock);
					this.actualPagingBlock = clonedPagingBlock;
				}
			}
	});
	$('.footable').trigger('footable_redraw');
	
	//add support for touchspint
	$(".touchspin3").TouchSpin({
			verticalbuttons: true,
			buttondown_class: 'btn btn-white',
			buttonup_class: 'btn btn-white',
			max: 1000000000
	});
	
	$(".bootstrap-touchspin-up").css("margin-bottom","0px");
	
	$(".colorpicker-component").colorpicker();
	$(".summernote:not(.x2_initialised)").addClass(".x2_initialised").each(function(index,element) {
		$(element).summernote({
			height: element.getAttribute("x2_height") ? element.getAttribute("x2_height") : "15em"
		});
	});
	$(".ckeditor:not(.x2_initialised)").addClass(".x2_initialised").each(function(index,element) {
		CKEDITOR.replace(element,{
			height: element.getAttribute("x2_height") ? element.getAttribute("x2_height") : "15em"
		});
	});
};

CX2.submitForm = function(form,controller,targetId,dialogId,view) {
	if(view) {
		view.formId = form.id;
		view.target = targetId;
		view.action = controller;
	} else {
		view = {
			formId: form.id,
			target: targetId,
			action: controller
		};
	}
	if(document.getElementById(targetId)) {
		document.getElementById(targetId).dialogId = dialogId;
	}
	CX2.invokeHandler({
		data: view
	});
};

CX2.invokeHandler = function(event) {
	var postFormData = function(actionDef) {
		//we will call our backend function
		var titleElm = document.getElementById("title-"+actionDef.target);
		if(titleElm && actionDef.description) {
			titleElm.innerHTML = actionDef.description;
		}
		//if this button is part of a form then we also need to include form data in the post. to do this the best thing to do would be to default to
		//a multipart/form-data post so we can include binary data in the post as well.
		var payloadData = JSON.stringify(actionDef);
		var contentType = null;
		CX2.updateEditors();
		if(actionDef.formId) {
			payloadData = new FormData(document.getElementById(actionDef.formId));
			payloadData.append("cx2.sys.view",JSON.stringify(actionDef));
			contentType = false;
		}
		CX2.runOnBackend("action/"+actionDef.action,CX2.runAction,CX2.handleBackendError,document.getElementById(actionDef.target),payloadData,contentType);
	};
	
	if(event.data.target && String(event.data.target).startsWith("javascript:")) {
		var jsCode = event.data.target.substring(event.data.target.indexOf(':')+1);
		try {
			eval(jsCode);
		}catch(e) {
			CX2.handleBackendError({ responseText: e.message+' - '+e.stack },400,e.message);
			throw e;
		}
		CX2.enableActiveButton();
	} else if(!event.data.target && CX2.activeDialog) {
		//we should check if a dialog is open and if it is we should simply dismiss it.
		if(CX2.activeDialog) {
			//we need to hide the dialog and set the active dialog to null
			$(CX2.activeDialog).modal('hide');
			CX2.activeDialog = null;
			if(event.data.action) {
				postFormData(event.data);
			} else {
				CX2.enableActiveButton();
			}
		} else {
			CX2.enableActiveButton();
		}
	} else {
		postFormData(event.data);
	}
};

CX2.util = {
	/** this function will find the form field with the name specified in the nearest form to the element specified **/
	getField: function(fieldName) {
		return $(event.currentTarget).closest("form").find("[name='"+fieldName+"']").get(0);
	},
	UUID: function() {
		function s4() {
			return Math.floor((1 + Math.random()) * 0x10000)
				.toString(16)
				.substring(1);
		}
		return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
			s4() + '-' + s4() + s4() + s4();
	},
	getUrlParameter: function(name) {
		if(name=(new RegExp('[?&]'+encodeURIComponent(name)+'=([^&]*)')).exec(location.search))
			 return decodeURIComponent(name[1]);
	}
};

CX2.components = { 
	image_view: function(viewDefinition) {
		var cmp = document.createElement("div");
		/** <img width=\"48\" alt=\"image\" class=\"img-circle\" 
		 * src=\""+document.location.pathname+"/authentication/profile_picture?token="
		 * +encodeURIComponent(CX2.user_token)+"\">**/
		var img = cmp.appendChild(document.createElement("img"));
		img.width = "96";
		img.height = "96";
		img.className = "img-circle";
		img.src = viewDefinition.src;
		if(viewDefinition.edit) {
			cmp.appendChild(CX2.components.binary(viewDefinition));
		}
		return cmp;
	},
	user_menu: function(viewDefinition) {
		var result = new Array();
		for(var i = 0; i < viewDefinition.items.length; i++) {
			var item = viewDefinition.items[i];
			var itemElm = document.createElement("li");
			if(item.divider) {
				itemElm.className = "divider";
			} else {
				var itemLinkElm = itemElm.appendChild(document.createElement("a"));
				itemLinkElm.innerHTML = item.title;
				$(itemLinkElm).on('click',item,function(event) {
					CX2.invokeHandler(event);
				});
			}
			result.push(itemElm);
		}
		return result;
	},
	embedded_web: function(viewDefinition) {
		var ifrm = document.createElement("iframe");
		ifrm.src = viewDefinition.url;
		ifrm.setAttribute("frameborder","0");
		if(viewDefinition.style) {
			ifrm.style = viewDefinition.style;
		}
		ifrm.style.width = "100%";
		ifrm.style.height = (window.innerHeight-(document.getElementById("feature-body").offsetTop+document.getElementById("feature-body").offsetHeight))+"px";
		return ifrm;
	},
	top_menu: function(viewDefinition) {
		var result = new Array();
		var titleElm = document.createElement("li");
		result.push(titleElm);
		titleElm.innerHTML = "<a class=\"m-r-sm text-muted welcome-message\">"+viewDefinition.title+"</a>";
		if(viewDefinition.showLogout) {
			var logoutElm = document.createElement("li");
			result.push(logoutElm);
			logoutElm.innerHTML = "<a href=\""+document.location.href.replace(/\?(.*)/g,"")+"\"><i class=\"fa fa-sign-out\"></i> Log out</a>";
		}
		if(viewDefinition.items.length !== 0) {
			var itemMenu = document.createElement("li");
			var itemMenuLink = itemMenu.appendChild(document.createElement("a"));
			itemMenuLink.className = "right-sidebar-toggle";
			itemMenuLink.innerHTML = "<i class=\"fa fa-tasks\"></i>";
			$(itemMenuLink).on('click',function() {
				//we should draw the right side bar
				var rightSideBar = document.getElementById("right-sidebar");
				if(rightSideBar) {
					//we should remove it.
					$(rightSideBar).remove();
				} else {
					rightSideBar = document.createElement("div");
					rightSideBar.id = "right-sidebar";
					rightSideBar.className = "sidebar-open";
					var slimScrollDiv = rightSideBar.appendChild(document.createElement("div"));
					slimScrollDiv.className = "slimScrollDiv";
					slimScrollDiv.style = "position: relative; overflow-x: hidden; overflow-y: auto; width: auto; height: 100%;";
					var sideBarContainer = slimScrollDiv.appendChild(document.createElement("div"));
					sideBarContainer.className = "sidebar-container";
					sideBarContainer.style = "overflow: hidden; width: auto; height: 100%;";
					var ul = sideBarContainer.appendChild(document.createElement("ul"));
					ul.className = "nav nav-tabs navs-"+(viewDefinition.items.length+1);
					var firstItem = null;
					for(var i = 0; i < viewDefinition.items.length; i++) {
						var menuItem = viewDefinition.items[i];
						var li = ul.appendChild(document.createElement("li"));
						if(i === 0) {
							li.className = "active";
							firstItem = menuItem;
						}
						var aRef = li.appendChild(document.createElement("a"));
						aRef.setAttribute("data-toggle","tab");
						aRef.setAttribute("href","#tab-"+i);
						aRef.setAttribute("aria-expanded",i === 0 ? "true" : "false");
						aRef.innerHTML = menuItem.title;
						$(aRef).on('click',menuItem, function(event) {
							CX2.invokeHandler(event);
						});
					}
					var liFiller = ul.appendChild(document.createElement("li"));
					liFiller.innerHTML = "<a style='color: #2f4050;'>_</a>";
					var tabContent = sideBarContainer.appendChild(document.createElement("div"));
					tabContent.className = "tab-content";
					for(var i = 0; i < viewDefinition.items.length; i++) {
						var tabContentItem = tabContent.appendChild(document.createElement("div"));
						tabContentItem.className = "tab-pane"+(i === 0 ? " active" : "");
						tabContentItem.id="tab-"+i;
						if(viewDefinition.items[i].subfeatures.length !== 0) {
							var tabTitle = tabContentItem.appendChild(document.createElement("div"));
							tabTitle.className = "sidebar-title";
							var subFeaturesElm = tabTitle.appendChild(document.createElement("ul"));
							subFeaturesElm.className = "list-group";
							for(var j = 0; j < viewDefinition.items[i].subfeatures.length; j++) {
								var subFeature = viewDefinition.items[i].subfeatures[j];
								//subFeature.target = "feature-body";
								var subFeatureElm = subFeaturesElm.appendChild(document.createElement("li"));
								subFeatureElm.className = "list-group-item";
								var subFeatureActionElm = subFeatureElm.appendChild(document.createElement("a"));
								subFeatureActionElm.innerHTML = "<i class='fa "+subFeature.icon+"'></i><span style='margin-left: 0.3em'>"+subFeature.title+"</span>";
								$(subFeatureActionElm).on("click",subFeature,function(event) {
									CX2.invokeHandler(event);
								});
							}
						}
					}
					$("#wrapper").append(rightSideBar);
					//we should load the dynamic content for the first menu item (like calling a feature)
					if(firstItem && firstItem.action) {
						CX2.invokeHandler({ data: firstItem });
					}
				}
			});
			result.push(itemMenu);
		}
		return result;
	},
	window: function(viewDefinition) {
		var winElm = document.createElement("div");
		winElm.className = "col-lg-"+viewDefinition.width;
		if(viewDefinition.style) {
			winElm.style = viewDefinition.style;
		}
		var winIboxElm = null;
		if(viewDefinition.formid) {
			var winFormElm = winElm.appendChild(document.createElement("form"));
			winFormElm.id = viewDefinition.formid;
			winIboxElm = winFormElm.appendChild(document.createElement("div"));
		} else {
			winIboxElm = winElm.appendChild(document.createElement("div"));
		}
		winIboxElm.className = "ibox float-e-margins";
		var winTitleElm = winIboxElm.appendChild(document.createElement("div"));
		winTitleElm.className = "ibox-title";
		winTitleElm.innerHTML = "<h5 id=\"title-"+viewDefinition.id+"\">"+viewDefinition.title+"</h5> <div class=\"ibox-tools\">"
						+(viewDefinition.collapsable ? "<a class=\"collapse-link\"><i class=\"fa fa-chevron-up\"></i></a>" : "")
						+(viewDefinition.closeable ? "<a class=\"close-link\"><i class=\"fa fa-times\"></i></a>" : "")+"</div>";
		if(viewDefinition.buttons) {
			var winTitleActions = winTitleElm.getElementsByTagName("div")[0];
			for(var i = 0; i < viewDefinition.buttons.length; i++) {
				var btnDef = viewDefinition.buttons[i];
				var btnElm = CX2.components.button(btnDef);
				btnElm.style.marginLeft = "0.5em";
				$(winTitleActions).prepend(btnElm);
			}
		}
		var winBodyElm = winIboxElm.appendChild(document.createElement("div"));
		winBodyElm.className = "ibox-content";
		winBodyElm.style.display = "block";
		winBodyElm.id = viewDefinition.id;
		if(viewDefinition.rows) {
			for(var i = 0; i < viewDefinition.rows.length; i++) {
				var row = viewDefinition.rows[i];
				winBodyElm.appendChild(CX2.components.row(row));
			}
		}
		if(viewDefinition.body) {
			viewDefinition.body(winBodyElm);
		}
		return winElm;
	},
	table_body: function(tblElm,data) {
		//we should build the table data here.
		var findCell = function(dataRow,column) {
			for(var i = 0; i < dataRow.cells.length; i++) {
				var dataCell = dataRow.cells[i];
				if(dataCell.name === column.key) {
					return dataCell;
				}
			}
			return null;
		};
		
		var buildCell = function(column,dataRow) {
			var cell = findCell(dataRow,column);
			var tblCell = document.createElement("td");
			if(column.size !== 0) {
				tblCell.style.width=(column.size)+"em";
				tblCell.style.minWidth = tblCell.style.width;
				tblCell.style.maxWidth = tblCell.style.width;
			}
			if(!column.visible) {
				tblCell.style.display = "none";
			}
			buildCellContents(cell,tblCell);
			
			return tblCell;
		};
		
		var buildCellContents = function(cell,tblCell) {
			if(cell.view) {
				//this will be an inline UI component which should be appended in the context of the table row.
				CX2.buildComponent(cell.view,tblCell);
			} else {
				var reLink = /(http)(s){0,1}:\/\/((([a-zA-Z0-9\-]+)(\.|$))+)/gi;
				var val = (cell.value !== null && cell.value ? cell.value : "");
				if(reLink.test(val)) {
					tblCell.innerHTML = "<a target=\"_blank\" href=\""+val+"\"><span style=\"white-space: pre-wrap; word-wrap: break-word; display: inline;\">"+val+"</span></a>";
				} else {
					//we need to get any javascript in the value element and run it
					var dv = document.createElement("div");
					dv.innerHTML = val;
					var scriptElmList = dv.getElementsByTagName("script");
					var scriptArr = new Array();
					for(var i = 0; i < scriptElmList.length; i++) {
						var scriptElm = scriptElmList[i];
						scriptArr.push(scriptElm);
					}
					for(var i = 0; i < scriptArr.length; i++) {
						var scriptElm = scriptArr[i];
						$(scriptElm).detach();
					}
 					$(tblCell).html("<span style=\"white-space: pre-wrap; word-wrap: break-word; display: inline;\">"+dv.innerHTML+"</span>");
					for(var i = 0; i < scriptArr.length; i++) {
						var scriptElm = scriptArr[i];
						try {
							eval($(scriptElm).text());
						}catch(e) {
							console.log("Script: "+$(scriptElm).text()+" error "+e);
						}
					}
				}
			}
		};
		
		var bodyElmList = tblElm.getElementsByTagName("tbody");
		var tblBody = null;
		if(bodyElmList && bodyElmList.length > 0) {
			tblBody = bodyElmList[0];
			tblBody.innerHTML = ""; //clear the data inside of the body.
		} else {
			tblBody = tblElm.appendChild(document.createElement("tbody"));
		}
		
		//we need to translate the data array into a columns by sections array.
		var dataMap = {};
		for(var i = 0; i < tblElm.config.columns.length; i++) {
			var sectionColumnList = dataMap[tblElm.config.columns[i].section.key];
			if(!sectionColumnList) {
				sectionColumnList = new Array();
				dataMap[tblElm.config.columns[i].section.key] = sectionColumnList;
			}
			sectionColumnList.push(tblElm.config.columns[i]);
		}
		
		for(var k = 0; k < data.length; k++) {
			var dataRow = data[k];
			var tblRow = tblBody.appendChild(document.createElement("tr"));
			//the first part we have to build is the "TABLE" section
			for(var i = 0; i < dataMap["TABLE"].length; i++) {
				var column = dataMap["TABLE"][i];
				tblRow.appendChild(buildCell(column,dataRow));
			}
			//next we have to build all of the other sections
			var sectionList = tblElm.config.sections;
			var otherSectionContainerElm = null;
			var otherSectionRowElm = null;
			for(var i = 0; i < sectionList.length; i++) {
				var section = sectionList[i];
				if(section.key !== "TABLE") {
					if(!otherSectionContainerElm) {
						otherSectionContainerElm = tblRow.appendChild(document.createElement("td"));
						//if this is in a dialog we need to make sure it is not visible.
						otherSectionContainerElm.style.display = "none";
					}
					if(i % 3 === 0) {
						otherSectionRowElm = otherSectionContainerElm.appendChild(document.createElement("div"));
						otherSectionRowElm.className = "row";
						otherSectionRowElm.style = "position: relative; top: -1em; background-color: white; border-bottom: 0px solid #f5f5f5;";
					}
					//this is a valid section so we need to build a row place holder for it.
					var sectionPanel = otherSectionRowElm.appendChild(document.createElement("div"));
					sectionPanel.className = "col-lg-4";
					sectionPanel.style = "padding-left: 0.5em; padding-right: 0;";
					//add a colpsable section title here if the section is not titled SECTION
					if(section.key !== "SECTION") {
						var winElm = sectionPanel.appendChild(document.createElement("div"));
						winElm.className = "col-lg-12";
						winElm.style = "padding-left: 0px; padding-right: 0px;";
						var panelElm = winElm.appendChild(document.createElement("div"));
						panelElm.className = "panel panel-primary";
						panelElm.style = "margin-bottom: 0em; border-width: 0px;";
						var panelHeader = panelElm.appendChild(document.createElement("div"));
						panelHeader.className = "panel-heading";
						panelHeader.style = "padding-bottom: 0.1em; padding-top: 0.2em;";
						var panelTitle = panelHeader.appendChild(document.createElement("h5"));
						panelTitle.style = "font-size: 11pt; font-weight: normal;";
						panelTitle.innerHTML = section.title;
						var panelBody = panelElm.appendChild(document.createElement("div"));
						panelBody.className = "panel-body";
						panelBody.style = "display: block; overflow: auto; padding-top: 0.2em; padding-bottom: 0.2em;";
						for(var j = 0; j < dataMap[section.key].length; j++) {
							var column = dataMap[section.key][j];
							var cell = findCell(dataRow,column);
							var sectionItemElm = panelBody.appendChild(document.createElement("div"));
							sectionItemElm.className = "col-lg-12";
							sectionItemElm.style = "padding-left: 0; padding-right: 0;";
							var sectionItemLabelElm = sectionItemElm.appendChild(document.createElement("span"));
							sectionItemLabelElm.innerHTML = "<b>"+column.name+"</b>&nbsp;";
							var sectionItemDataElm = sectionItemElm.appendChild(document.createElement("span"));
							buildCellContents(cell,sectionItemDataElm);
						}
						
						sectionPanel.appendChild(winElm);
					} else {
						for(var j = 0; j < dataMap[section.key].length; j++) {
							var column = dataMap[section.key][j];
							var cell = findCell(dataRow,column);
							var sectionItemElm = sectionPanel.appendChild(document.createElement("div"));
							sectionItemElm.className = "col-lg-12";
							var sectionItemLabelElm = sectionItemElm.appendChild(document.createElement("span"));
							sectionItemLabelElm.innerHTML = "<b>"+column.name+"</b>&nbsp;";
							var sectionItemDataElm = sectionItemElm.appendChild(document.createElement("span"));
							buildCellContents(cell,sectionItemDataElm);
						}
					}
				}
			}
			if(tblElm.config.legends) {
				var tblCell = tblRow.appendChild(document.createElement("td"));
				var tblCellContainer = tblCell.appendChild(document.createElement("div"));
				tblCell.style = "text-align: right;";
				for(var i = 0; i < dataRow.legends.length; i++) {
					var legend = dataRow.legends[i];
					var legendElm = tblCellContainer.appendChild(document.createElement("span"));
					if(legend.class) {
						legendElm.className = legend.class;
					} else {
						legendElm.className = "label";
					}
					if(legend.color) {
						legendElm.setAttribute("style","background-color: "+legend.color+"; color: #ffffff; margin-right: 0.2em;");
					} else {
						legendElm.setAttribute("style","background-color: #1bb394; color: #ffffff; margin-right: 0.2em;");
					}
					if(legend.icon) {
						iconElm = legendElm.appendChild(document.createElement("i"));
						iconElm.className = "fa "+legend.icon;
					}
					if(legend.title) {
						titleElm = legendElm.appendChild(document.createElement("span"));
						if(legend.icon) {
							titleElm.setAttribute("style","padding-left: 0.3em");
						}
						titleElm.innerHTML = legend.title;
					} else if(!legend.icon) {
						legendElm.innerHTML = "&nbsp;";
					}
					if(legend.action) {
						$(legendElm).on('click',legend,function(event) {
							CX2.invokeHandler(event);
						});
					}
				}
			}
			if(tblElm.config.actions && tblElm.config.actions.length > 0) {
				var tblCell = tblRow.appendChild(document.createElement("td"));
				tblCell.style = "white-space: nowrap";
				for(var i = 0; i < tblElm.config.actions.length; i++) {
					if(!dataRow["exclude-actions"] || !dataRow["exclude-actions"].includes(tblElm.config.actions[i].title)) {
						var btn = tblElm.config.actions[i];
						btn.attributes.row = JSON.stringify(dataRow);
						var btnContainer = tblCell.appendChild(document.createElement("span"));
						btnContainer.style.paddingRight = "0.5em";
						btnContainer.appendChild(CX2.components.button(btn));
					}
				}
			}
		}
		
		//this code will pivot the footer so it's not impacted by the theme application
		/*var footerElm = null;
		var footerList = tblElm.getElementsByTagName("tfoot");
		if(footerList !== null && footerList.length > 0) {
			footerElm = footerList[0];
		}
		if(footerElm) {
			tblElm.removeChild(footerElm);
		}*/
		CX2.applyTheme();
		/*if(footerElm) {
			tblElm.appendChild(footerElm);
		}*/
	},
	table: function(viewDefinition) {
		//we will build a shell for the table and then we will trigger an event against the backing component for the table
		//which will provide us with the data that is currently in view.
		var tableElm = document.createElement("table");
		tableElm.className = "footable table table-stripped toggle-arrow-tiny";
		tableElm.setAttribute("data-page-size",viewDefinition.pagesize);
		tableElm.config = viewDefinition;
		var tableHeadElm = tableElm.appendChild(document.createElement("thead"));
		var tableHeadRowElm = tableHeadElm.appendChild(document.createElement("tr"));
		var firstColumn = null;
		var tableSectionMap = {};
		var ctr = 0;
		for(var i = 0; i < viewDefinition.columns.length; i++) {
			var column = viewDefinition.columns[i];
			if(column.section.key !== "TABLE") {
				if(!tableSectionMap[column.section]) {
					tableSectionMap[column.section] = new Array();
				}
				tableSectionMap[column.section].push(column);
			} else {
				if(firstColumn === null) {
					firstColumn = column;
				}
				var thElm = tableHeadRowElm.appendChild(document.createElement("th"));
				thElm.className = "no-sort";
				if(column.size !== 0) {
					thElm.style.width=(column.size)+"em";
					thElm.style.minWidth = thElm.style.width;
					thElm.style.maxWidth = thElm.style.width;
				}
				if(ctr === 0) {
					thElm.setAttribute("data-toggle","true");
				}
				thElm.innerHTML = column.name;
				if(viewDefinition.allow_sort) {
					var sortSpn = thElm.appendChild(document.createElement("div"));
					sortSpn.className = "x2-table-sort-indicator";
					$(sortSpn).on("click",{ tableElm: tableElm, orderBy: column.key }, function(event) {
						var tableElm = event.data.tableElm;
						var orderDirection = tableElm.orderDirection;
						if(tableElm.orderBy === event.data.orderBy) {
							if(orderDirection === "ASC") {
								orderDirection = "DESC";
							} else {
								orderDirection = "ASC";
							}
						} else {
							orderDirection = "ASC";
						}
						tableElm.orderBy = event.data.orderBy;
						tableElm.orderDirection = orderDirection;
						$(tableElm).find("tbody")[0].innerHTML = tableElm.waitingHTML;
						CX2.runOnBackend("action/onDataPage/"+tableElm.config.controller+"?page="+tableElm.page+"&orderby="+encodeURIComponent(event.data.orderBy)+"&order_direction="+encodeURIComponent(orderDirection),function(data) { CX2.components.table_body(this,data); },
														 CX2.handleBackendError,tableElm,JSON.stringify(tableElm.config),"application/json");
					});
				}
				ctr++;
			}
		}
		//now for each of the remaining keys in the section map we will need to create a single hidden column in this table
		var sectionKeyList = Object.keys(tableSectionMap);
		if(sectionKeyList.length !== 0) {
			var thElm = tableHeadRowElm.appendChild(document.createElement("th"));
			thElm.setAttribute("data-hide","all");
			thElm.style.display = "none";
		}
		if(viewDefinition.legends) {
			var th = tableHeadRowElm.appendChild(document.createElement("th"));
			th.className = "no-sort";
			th.setAttribute("width","1"); //add a simple action header
		}
		if(viewDefinition.actions && viewDefinition.actions.length > 0) {
			var th = tableHeadRowElm.appendChild(document.createElement("th"));
			th.className = "no-sort";
			th.setAttribute("width","1"); //add a simple action header
		}
		if(!tableElm.orderBy) {
			tableElm.orderBy = firstColumn.key;
			tableElm.orderDirection = "ASC";
		}
		tableElm.page = 1;
		//we now need to launch two separate events - the first will ask for the data in the table (the rows)
		CX2.runOnBackend("action/onDataPage/"+viewDefinition.controller+"?page=1&orderby="+encodeURIComponent(tableElm.orderBy),function(data) {
			CX2.components.table_body(this,data);
			var onDataSize = function(tblElm, data) {
				if(tblElm.config.show_page_numbers) {
					tblElm.totalRows = data;
					tblElm.numPages = data > 0 ? Math.round((data / tblElm.config.pagesize)) : 1;
					if(tblElm.numPages * tblElm.config.pagesize < tblElm.totalRows) {
						tblElm.numPages++;
					}
				}
				var tblFooterElm = tblElm.appendChild(document.createElement("tfoot"));
				var tblFooterRowElm = tblFooterElm.appendChild(document.createElement("tr"));
				var tblFooterColElm = tblFooterRowElm.appendChild(document.createElement("td"));
				tblFooterColElm.setAttribute("colspan",tblElm.visibleColumns);
				tblFooterColElm.className = "footable-visible";
				var pagingContainerElm = tblFooterColElm.appendChild(document.createElement("ul"));
				pagingContainerElm.className = "x2-table-paging pull-right";
				var pagingFirstElm = pagingContainerElm.appendChild(document.createElement("li"));
				pagingFirstElm.className = "footable-page-arrow disabled";
				var pagingFirstActionElm = pagingFirstElm.appendChild(document.createElement("a"));
				pagingFirstActionElm.setAttribute("data-page","first");
				pagingFirstActionElm.innerHTML = "«";
				$(pagingFirstActionElm).on("click",tblElm,function(event) {
					event.data.page = 1;
					$("a[data-page = 'next']").parent().removeClass("disabled");
					$("a[data-page = 'last']").parent().removeClass("disabled");
					$("a[data-page = 'prev']").parent().addClass("disabled");
					$("a[data-page = 'first']").parent().addClass("disabled");
					$("li.footable-page").removeClass("active");
					$("a[data-page = '"+event.data.page+"']").parent().addClass("active");
					$(event.data).find("tbody")[0].innerHTML = event.data.waitingHTML;
					CX2.runOnBackend("action/onDataPage/"+event.data.config.controller+"?page=1&orderby="+encodeURIComponent(event.data.orderBy),function(data) { CX2.components.table_body(this,data); },
													 CX2.handleBackendError,event.data,JSON.stringify(event.data.config),"application/json");
				});
				var pagingBackElm = pagingContainerElm.appendChild(document.createElement("li"));
				pagingBackElm.className = "footable-page-arrow disabled";
				var pagingBackActionElm = pagingBackElm.appendChild(document.createElement("a"));
				pagingBackActionElm.setAttribute("data-page","prev");
				pagingBackActionElm.innerHTML = "‹";
				$(pagingBackActionElm).on("click",tblElm,function(event) {
					event.data.page--;
					$("a[data-page = 'next']").parent().removeClass("disabled");
					$("a[data-page = 'last']").parent().removeClass("disabled");
					$("li.footable-page").removeClass("active");
					$("a[data-page = '"+event.data.page+"']").parent().addClass("active");
					if(event.data.page === 1) {
						$("a[data-page = 'prev']").parent().addClass("disabled");
						$("a[data-page = 'first']").parent().addClass("disabled");
					} else {
						$("a[data-page = 'prev']").parent().removeClass("disabled");
						$("a[data-page = 'first']").parent().removeClass("disabled");
					}
					$(event.data).find("tbody")[0].innerHTML = event.data.waitingHTML;
					CX2.runOnBackend("action/onDataPage/"+event.data.config.controller+"?page="+(event.data.page)+"&orderby="+encodeURIComponent(event.data.orderBy),function(data) { CX2.components.table_body(this,data); },
													 CX2.handleBackendError,event.data,JSON.stringify(event.data.config),"application/json");
				});
				//here we should add all of the specific page numbers.
				if(tblElm.config.show_page_numbers) {
					for(var i = 1; i <= tblElm.numPages; i++) {
						var pagingPageElm = pagingContainerElm.appendChild(document.createElement("li"));
						pagingPageElm.className = "footable-page"+(i === tblElm.page ? " active" : "");
						var pagingPageActionElm = pagingPageElm.appendChild(document.createElement("a"));
						pagingPageActionElm.setAttribute("data-page",i);
						pagingPageActionElm.innerHTML = i;
						$(pagingPageActionElm).on("click",tblElm,function(event) {
							event.data.page = parseInt(this.getAttribute("data-page"));
							$("li.footable-page").removeClass("active");
							$("a[data-page = '"+event.data.page+"']").parent().addClass("active");
							if(event.data.page === 1) {
								$("a[data-page = 'prev']").parent().addClass("disabled");
								$("a[data-page = 'first']").parent().addClass("disabled");
							} else {
								$("a[data-page = 'prev']").parent().removeClass("disabled");
								$("a[data-page = 'first']").parent().removeClass("disabled");
							}
							if(event.data.page >= event.data.numPages) {
								$("a[data-page = 'next']").parent().addClass("disabled");
								$("a[data-page = 'last']").parent().addClass("disabled");
							} else {
								$("a[data-page = 'next']").parent().removeClass("disabled");
								$("a[data-page = 'last']").parent().removeClass("disabled");
							}
							$(event.data).find("tbody")[0].innerHTML = event.data.waitingHTML;
							CX2.runOnBackend("action/onDataPage/"+event.data.config.controller+"?page="+(event.data.page)+"&orderby="+encodeURIComponent(event.data.orderBy),function(data) { CX2.components.table_body(this,data); },
															 CX2.handleBackendError,event.data,JSON.stringify(event.data.config),"application/json");
						});
					}
				}
				
				var pagingNextElm = pagingContainerElm.appendChild(document.createElement("li"));
				pagingNextElm.className = "footable-page-arrow"+((tblElm.page !== tblElm.numPages) ? "" : " disabled");
				var pagingNextActionElm = pagingNextElm.appendChild(document.createElement("a"));
				pagingNextActionElm.setAttribute("data-page","next");
				pagingNextActionElm.innerHTML = "›";
				$(pagingNextActionElm).on("click",tblElm,function(event) {
					//we should also add or remove the disabled class from the prev and next buttons.
					event.data.page++;
					$("a[data-page = 'first']").parent().removeClass("disabled");
					$("a[data-page = 'prev']").parent().removeClass("disabled");
					$("li.footable-page").removeClass("active");
					$("a[data-page = '"+event.data.page+"']").parent().addClass("active");
					if((event.data.page * event.data.config.pagesize) >= event.data.totalRows) {
						$("a[data-page = 'next']").parent().addClass("disabled");
						$("a[data-page = 'last']").parent().addClass("disabled");
					} else {
						$("a[data-page = 'next']").parent().removeClass("disabled");
						$("a[data-page = 'last']").parent().removeClass("disabled");
					}
					$(event.data).find("tbody")[0].innerHTML = event.data.waitingHTML;
					CX2.runOnBackend("action/onDataPage/"+event.data.config.controller+"?page="+event.data.page+"&orderby="+encodeURIComponent(event.data.orderBy),function(data) { CX2.components.table_body(this,data); },
													 CX2.handleBackendError,event.data,JSON.stringify(event.data.config),"application/json");
				});
				if(tblElm.config.show_page_numbers) {
					var pagingLastElm = pagingContainerElm.appendChild(document.createElement("li"));
					pagingLastElm.className = "footable-page-arrow"+((parseInt(tblElm.page) !== parseInt(tblElm.numPages)) ? "" : " disabled");
					var pagingLastActionElm = pagingLastElm.appendChild(document.createElement("a"));
					pagingLastActionElm.setAttribute("data-page","last");
					pagingLastActionElm.innerHTML = "»";
					$(pagingLastActionElm).on("click",tblElm,function(event) {
						//we should also add or remove the disabled class from the prev and next buttons.
						event.data.page = event.data.numPages;
						$("a[data-page = 'first']").parent().removeClass("disabled");
						$("a[data-page = 'prev']").parent().removeClass("disabled");
						$("a[data-page = 'next']").parent().addClass("disabled");
						$("a[data-page = 'last']").parent().addClass("disabled");
						$("li.footable-page").removeClass("active");
						$("a[data-page = '"+event.data.page+"']").parent().addClass("active");
						$(event.data).find("tbody")[0].innerHTML = event.data.waitingHTML;
						CX2.runOnBackend("action/onDataPage/"+event.data.config.controller+"?page="+event.data.numPages+"&orderby="+encodeURIComponent(event.data.orderBy),function(data) { CX2.components.table_body(this,data); },
														 CX2.handleBackendError,event.data,JSON.stringify(event.data.config),"application/json");
					});
					var totalElm = pagingContainerElm.appendChild(document.createElement("li"));
					var totalSpanElm = totalElm.appendChild(document.createElement("span"));
					totalSpanElm.innerHTML = "&nbsp;";
					var totalQuantity = totalSpanElm.appendChild(document.createElement("span"));
					totalQuantity.className = "label label-success";
					totalQuantity.innerHTML = tblElm.totalRows;
				}
			};
			if(this.config.show_page_numbers) {
				CX2.runOnBackend("action/onDataSize/"+this.config.controller,function(data) { onDataSize(this,data); },CX2.handleBackendError,this,JSON.stringify(this.config),"application/json");
			} else {
				onDataSize(this); //just pass null the value should not be used anyway.
			}
		},CX2.handleBackendError,tableElm,JSON.stringify(viewDefinition),"application/json");
		//we are also going to need to ask how much data we should actually display in here.
		var visibleColumns = 0;
		for(var i = 0; i < tableElm.config.columns.length; i++) {
			if(tableElm.config.columns[i].visible) {
				visibleColumns++;
			}
		}
		if(tableElm.config.actions.length > 0) {
			visibleColumns++;
		}
		if(tableElm.config.legends) {
			visibleColumns++;
		}
		tableElm.visibleColumns = visibleColumns;
		tableElm.waitingHTML = "<tr><td align='center' colspan='"+tableElm.visibleColumns+"'><div class=\"alert alert-info\">Loading Results ... Please Wait</div></td></tr>";
		return tableElm;
	},
	button: function(btnDef) {
		var btn = document.createElement("button");
		btn.type = "button";
		btn.className = "btn btn-primary btn-sm"+(btnDef.extendedclass ? " "+btnDef.extendedclass : "");
		btn.id = CX2.util.UUID();
		if(btnDef.style) {
			btn.style = btnDef.style;
			btn.setAttribute("style",btnDef.style);
		}
		//add support for implicitly hiding buttons from the json object model.
		if(btnDef.hidden) {
			$(btn).css("display","none");
		}
		if(btnDef.icon) {
			btn.innerHTML = "<i class=\"fa "+btnDef.icon+"\"></i>"+(btnDef.title ? btnDef.title : "");
		} else if(btnDef.title) {
			btn.innerHTML = btnDef.title;
		}
		var defStrCopy = JSON.stringify(btnDef);
		var clickAction = function(event) {
			//check if the button actually exists before run anything on it
			//we should disable the button when it is clicked
			if(document.getElementById(this.id)) {
				if(!CX2.activeButton) {
					CX2.activeButton = this;
					this.disabled = true;
					$(CX2.activeButton).addClass("disabled");
					CX2.invokeHandler(event);
				}
			} else if(this.interval) {
				window.clearInterval(this.interval); //if the element no longer exists there is no sense in preserving the interval
			}
		};

		$(btn).on("click",JSON.parse(defStrCopy),clickAction);
		//we may have set the execution mode on this button to run on a schedule or after a certain amount of time.
		if(btnDef.execution_mode === "TIMEOUT") {
			window.setTimeout($.proxy(clickAction,btn,{ data: JSON.parse(defStrCopy) }),btnDef.execution_interval);
		} else if(btnDef.execution_mode === "INTERVAL") {
			btn.interval = window.setInterval($.proxy(clickAction,btn,{ data: JSON.parse(defStrCopy) }),btnDef.execution_interval);
		}
		
		return btn;
	},
	form: function(viewDefinition) {
		var formElm = document.createElement("form");
		formElm.id = viewDefinition.id;
		if(viewDefinition.inline === true) {
			formElm.className = "form-inline";
		} else {
			formElm.className = "form-horizontal";
		}
		if(viewDefinition.description) {
			var formDescriptionElm = formElm.appendChild(document.createElement("p"));
			formDescriptionElm.innerHTML = viewDefinition.description;
		}
		for(var i = 0; i < viewDefinition.components.length; i++) {
			var formComponent = viewDefinition.components[i];
			var formGroupElm = formElm.appendChild(document.createElement("div"));
			formGroupElm.className = "form-group";
			formGroupElm.id = "x2_form_group_"+formComponent.name;
			if(formComponent.class !== "FormSectionView") {
				var formComponentLabelElm = formGroupElm.appendChild(document.createElement("label"));
				if(viewDefinition.inline === true) {
					formComponentLabelElm.className = "sr-only";
				} else {
					formComponentLabelElm.className = "col-lg-2 control-label";
				}
				formComponentLabelElm.innerHTML = formComponent.title;
			}
			if(viewDefinition.inline === true) {
				CX2.buildComponent(formComponent,formGroupElm); //needs to use a build component because we already know what the component is.
			} else {
				var formComponentContainerElm = formGroupElm.appendChild(document.createElement("div"));
				if(formComponent.class !== "FormSectionView") {
					formComponentContainerElm.className = "col-lg-10";
					CX2.buildComponent(formComponent,formComponentContainerElm); //needs to use a build component because we already know what the component is.
				} else {
					CX2.buildComponent(formComponent,formGroupElm); //needs to use a build component because we already know what the component is.
				}
			}
		}
		if(viewDefinition.buttons && viewDefinition.buttons.length > 0) {
			var btnContainerElm = null;
			if(viewDefinition.inline === true) {
				btnContainerElm = formElm.appendChild(document.createElement("span"));
				btnContainerElm.style = "position: relative; top: -5px;";
			} else {
				var buttonSection = formElm.appendChild(document.createElement("div"));
				buttonSection.className = "hr-line-dashed";
				var btnGroupElm = formElm.appendChild(document.createElement("div"));
				btnGroupElm.className = "form-group";
				btnContainerElm = btnGroupElm.appendChild(document.createElement("div"));
				btnContainerElm.className = "col-sm-10 col-sm-offset-2";
			}
			
			for(var i = 0; i < viewDefinition.buttons.length; i++) {
				btnContainerElm.appendChild(document.createTextNode(" "));
				btnContainerElm.appendChild(CX2.components.button(viewDefinition.buttons[i]));
			}
		}
		
		return formElm;
	},
	picklist: function(viewDefinition) {
		var formCompElm = document.createElement("select");
		formCompElm.className = "form-control m-b";
		formCompElm.name = viewDefinition.name;
		for(var i = 0; i < viewDefinition.values.length; i++) {
			var valueItem = viewDefinition.values[i];
			var optElm = formCompElm.appendChild(document.createElement("option"));
			optElm.setAttribute("value",valueItem.key);
			if(viewDefinition.selected === valueItem.key) {
				optElm.setAttribute("selected","yes");
			}
			optElm.innerHTML = valueItem.value;
		}
		
		return formCompElm;
	},
	text: function(viewDefinition) {
		var formCompElm = document.createElement("input");
		formCompElm.type = "text";
		formCompElm.name = viewDefinition.name;
		formCompElm.className = "form-control";
		if(viewDefinition.value) {
			formCompElm.value = viewDefinition.value;
		}
		if(viewDefinition.action) {
			$(formCompElm).on('keypress',viewDefinition,function(event) {
				if(event.which === 13) {
					CX2.invokeHandler(event);
				}
			});
		}
		
		return formCompElm;
	},
	code_editor: function(viewDefinition) {
		var formCompElm = document.createElement("textarea");
		formCompElm.name = viewDefinition.name;
		if(viewDefinition.value) {
			formCompElm.innerHTML = viewDefinition.value;
		}
		formCompElm.className = "codemirror";
		formCompElm.mode = viewDefinition.mode;
		
		return formCompElm;
	},
	row: function(viewDefinition) {
		var rowElm = document.createElement("div");
		rowElm.className = "row";
		//we need to construct whatever is in the row and a row is built of windows.
		CX2.buildComponents(viewDefinition,rowElm);
		
		return rowElm;
	},
	application_menu: function(viewDefinition) {
		var buildMenu = function(data,rootElement,level) {
			for(var i = 0; i < data.length; i++) {
				var feature = data[i];
				var menuItem = rootElement.appendChild(document.createElement("li"));
				var menuLink = menuItem.appendChild(document.createElement("a"));
				menuLink.href = "#";
				var levelClass = "nav-second-level";
				if(feature.backend) {
					var featureStr = JSON.stringify(feature);
					$(menuLink).on('click',JSON.parse(featureStr),function(event) {
						if(event.data.script) {
							try {
								eval(event.data.script);
							} catch(e) {
								CX2.handleBackendError({ responseText: e.message+' - '+e.stack },400,e.message);
								throw e;
							}
						} else {
							CX2.runOnBackend('feature/'+event.data.backend,CX2.runFeature,CX2.handleBackendError,document.getElementById("side-menu"),JSON.stringify(event.data),null);
						}
					});
				}
				if(level === 1) {
					menuLink.innerHTML = "<i class=\"fa "+feature.icon+"\"></i> <span class=\"nav-label\">"+feature.title+"</span> <span class=\"fa arrow\"></span>";
				} else {
					levelClass = "nav-third-level";
					menuLink.innerHTML = "<i class=\"fa "+feature.icon+"\"></i>"+feature.title;
				}
				if(feature.subfeatures.length > 0) {
					var subMenu = menuItem.appendChild(document.createElement("ul"));
					subMenu.className = "nav "+levelClass+" collapse";
					buildMenu(feature.subfeatures,subMenu,level+1);
				}
			}
		};
		var sideMenuElm = document.getElementById("side-menu");
		if(CX2.user) {
			sideMenuElm.innerHTML = "<li class=\"nav-header\"><div class=\"dropdown profile-element\"> <span>\n\
																	<img id=\"user_profile_image\" width=\"48\" height=\"48\" alt=\"image\" class=\"img-circle\" src=\""+(document.location.pathname === "/" ? "" : document.location.pathname)+"/authentication/profile_picture?token="+encodeURIComponent(CX2.user_token)+"&id="+encodeURIComponent(CX2.user.id)+"\">\n\
															 </span>\n\
															<a data-toggle=\"dropdown\" class=\"dropdown-toggle\" href=\"#\" aria-expanded=\"false\">\n\
															<span class=\"clear\"> <span class=\"block m-t-xs\"> <strong class=\"font-bold\">"+CX2.user.givenName+" "+CX2.user.surname+"</strong>\n\
															 </span> <span class=\"text-muted text-xs block\">("+CX2.user.userPrincipalName+")<b class=\"caret\"></b></span> </span> </a>\n\
																<ul class=\"dropdown-menu animated fadeInRight m-t-xs\" id=\"user-app-menu\">\n\
																		<li><a href=\""+(document.location.pathname === "/" ? "" : document.location.pathname)+"\">Logout</a></li>\n\
																</ul>\n\
														</div><div class=\"logo-element\">"+window.x2_app.key+"</div></li>";
			//we should run an event to load any supplimentary user focused menu items.
			CX2.runEvent('onUserMenu','user-app-menu',{},CX2.runAction);
		} else {
			sideMenuElm.innerHTML = "<li class=\"nav-header\"><div class=\"logo-element\">"+window.x2_app.key+"</div></li>";
		}
		buildMenu(viewDefinition,sideMenuElm,1);
		var sideMenu = $('#side-menu');
		sideMenu.removeData("mm");
		sideMenu.metisMenu();
	},
	form_section: function(viewDefinition) {
		var sectionElm = document.createElement("div");
		sectionElm.className = "col-lg-"+viewDefinition.size;
		var sectionPanelElm = sectionElm.appendChild(document.createElement("div"));
		sectionPanelElm.className = "panel panel-"+viewDefinition.importance;
		var sectionPanelHeaderElm = sectionPanelElm.appendChild(document.createElement("div"));
		sectionPanelHeaderElm.className = "panel-heading";
		sectionPanelHeaderElm.innerHTML = viewDefinition.title;
		var sectionPanelBodyElm = sectionPanelElm.appendChild(document.createElement("div"));
		sectionPanelBodyElm.className = "panel-body";
		CX2.buildComponents(viewDefinition,sectionPanelBodyElm);
		return sectionElm;
	},
	area: function(viewDefinition) {
		var areaElm = document.createElement("div");
		areaElm.className = "col-lg-"+viewDefinition.size;
		areaElm.setAttribute("id",viewDefinition.id);
		if(viewDefinition.style) {
			areaElm.setAttribute("style",viewDefinition.style);
		}
		CX2.buildComponents(viewDefinition,areaElm);
		return areaElm;
	},
	areaform: function(viewDefinition) {
		var areaElm = document.createElement("div");
		areaElm.className = "col-lg-"+viewDefinition.size;
		areaElm.setAttribute("id",viewDefinition.id);
		if(viewDefinition.style) {
			areaElm.setAttribute("style",viewDefinition.style);
		}
		var formElm = document.createElement("form");
		formElm.setAttribute("id",viewDefinition.form_id);
		formElm.setAttribute("style", viewDefinition.form_style);
		CX2.buildComponents(viewDefinition,formElm);
		areaElm.appendChild(formElm);
		return areaElm;
	},
	label: function(viewDefinition) {
		var labelElm = document.createElement("div");
		if(viewDefinition.style) {
			labelElm.style = viewDefinition.style;
		}
		labelElm.className = "col-lg-"+viewDefinition.size+(viewDefinition.extraclassdata ? " "+viewDefinition.extraclassdata : "");
		labelElm.innerHTML = "<b>"+viewDefinition.text+"</b>";
		return labelElm;
	},
	multiselect_picklist: function(viewDefinition) {
		var selectElm = document.createElement("select");
		selectElm.setAttribute("multiple","multiple");
		selectElm.name = viewDefinition.name;
		selectElm.style.height = viewDefinition.height+"px";
		selectElm.style.width = "100%";
		for(var i = 0; i < viewDefinition.values.length; i++) {
			var valueItem = viewDefinition.values[i];
			var optElm = selectElm.appendChild(document.createElement("option"));
			optElm.setAttribute("value",valueItem.key);
			for(var j = 0; j < viewDefinition.selected.length; j++) {
				if(viewDefinition.selected[j] === valueItem.key) {
					optElm.setAttribute("selected","yes");
				}
			}
			optElm.innerHTML = valueItem.value;
		}
		return selectElm;
	},
	inputbutton: function(viewDefinition) {
		var btnElm = document.createElement("div");
		btnElm.className = "input-group";
		var btnContainerElm = document.createElement("span");
		btnContainerElm.className = "input-group-btn";
		btnContainerElm.appendChild(CX2.components.button(viewDefinition));
		if(viewDefinition.position === "LEFT") {
			btnElm.appendChild(btnContainerElm);
			btnElm.appendChild(CX2.components.text(viewDefinition));
		} else {
			btnElm.appendChild(CX2.components.text(viewDefinition));
			btnElm.appendChild(btnContainerElm);
		}
		
		return btnElm;
	},
	data_island: function(viewDefinition) {
		var inputElm = document.createElement("input");
		inputElm.setAttribute("type","hidden");
		inputElm.setAttribute("name",viewDefinition.name);
		if(viewDefinition.value) {
			inputElm.setAttribute("value",viewDefinition.value);
		}
		return inputElm;
	},
	number: function(viewDefinition) {
		var inputElm = document.createElement("input");
		inputElm.setAttribute("type","text");
		inputElm.setAttribute("name",viewDefinition.name);
		inputElm.setAttribute("value",viewDefinition.value);
		inputElm.className = "touchspin3";
		return inputElm;
	},
	boolean: function(viewDefinition) {
		var container = document.createElement("div");
		container.className = "col-lg-12";
		var vals = [{ "value" : true, "label" : "Yes" },{ "value" : false, "label" : "No" }];
		for(var i = 0; i < vals.length; i++) {
			var viewContainer = container.appendChild(document.createElement("div"));
			var optionContainer = viewContainer.appendChild(document.createElement("label"));
			var input = optionContainer.appendChild(document.createElement("input"));
			input.setAttribute("type","radio");
			input.setAttribute("name", viewDefinition.name);
			input.setAttribute("value", vals[i].value);
			if(viewDefinition.value === vals[i].value) {
				input.setAttribute("checked","yes");
			}
			var spn = optionContainer.appendChild(document.createElement("span"));
			spn.setAttribute("style","margin-left: 0.5em;");
			spn.innerHTML = vals[i].label;
		}
		
		return container;
	},
	datalist: function(viewDefinition) {
		CX2.code.components.datalist.controller = viewDefinition.controller;
		var container = document.createElement("div");
		var dataListView = {
			name: viewDefinition.name+"_itemlist",
			values: [
				{ key: "", value: "-- Unselected --" }
			]
		};
		if(viewDefinition.allowNew) {
			dataListView.values.push({key: "$$NEW$$", value: "-- New "+viewDefinition.itemLabel+" --"});
		}
		var selectedListUUID = CX2.util.UUID();
		container.selectedDataListId = selectedListUUID;
		container.dataListId = CX2.util.UUID();
		var dataList = container.appendChild(CX2.components.picklist(dataListView));
		dataList.id = container.dataListId;
		$(dataList).on('change',{ component: container, view: viewDefinition, dataList: dataList }, function(event) {
			var selectedItem = event.data.dataList.options[event.data.dataList.selectedIndex].value;
			if(selectedItem === "$$NEW$$") {
				//we should create an overlay where we will display the form described in our view definition
				//we also need to automatically add some buttons to the form for saving and canceling the interaction with this dialog.
				CX2.code.components.datalist.showEditDialog({ view: event.data.view, selectedDataListId: event.data.component.selectedDataListId, dataListId: event.data.component.dataListId, dataForm: event.data.view.view });
			} else if(selectedItem !== "") {
				//in this case we should build the form using the view associated to the single data item.
				var optionElm = event.data.dataList.options[event.data.dataList.selectedIndex];
				CX2.code.components.datalist.addItem(optionElm.item,document.getElementById(event.data.component.selectedDataListId),event.data.view.allowEdit);
				//we now need to remove this element from the selection list.
				$(optionElm).remove();
				event.data.dataList.selectedIndex = 0;
			}
		});
		var selectedDataList = container.appendChild(document.createElement("ul"));
		selectedDataList.setAttribute("style","padding: 0px; list-style-type: none;");
		selectedDataList.id = selectedListUUID;
		if(viewDefinition.allowSort) {
			$(selectedDataList).sortable({
				update: function(event,ui) {
					CX2.code.components.datalist.sync(this.id);
				}
			});
		}
		if(viewDefinition.items) {
			for(var i = 0; i < viewDefinition.items.length; i++) {
				var item = viewDefinition.items[i];
				CX2.code.components.datalist.addItem(item,selectedDataList,viewDefinition.allowEdit);
			}
		}
		var dataListValue = container.appendChild(document.createElement("input"));
		dataListValue.setAttribute("type","hidden");
		dataListValue.setAttribute("name",viewDefinition.name);
		dataListValue.setAttribute("id","value_"+selectedListUUID);
		window.setTimeout(CX2.code.components.datalist.sync,10,selectedListUUID);
		return container;
	},
	dialog: function(viewDefinition) {
		var dialog = document.createElement("div");
		dialog.id = viewDefinition.id;
		dialog.className = "modal fade";
		var dialogInner = dialog.appendChild(document.createElement("div"));
		dialogInner.className = "modal-dialog";
		if(viewDefinition.width) {
			dialogInner.style.width = viewDefinition.width;
		}
		var dialogContent = dialogInner.appendChild(document.createElement("div"));
		dialogContent.className = "modal-content";
		var dialogBody = dialogContent.appendChild(document.createElement("div"));
		dialogBody.className = "modal-body";
		dialogBody.style.padding = "0.5em";
		CX2.buildComponents(viewDefinition,dialogBody);
		return dialog;
	},
	tab: function(viewDefinition) {
		var tabContainer = document.createElement("div");
		tabContainer.className = "panel-body";
		var tab = tabContainer.appendChild(document.createElement("div"));
		tab.className = "tabs-container";
		var tabList = tab.appendChild(document.createElement("ul"));
		tabList.className = "nav nav-tabs";
		for(var i = 0; i < viewDefinition.tabs.length; i++) {
			var tabView = viewDefinition.tabs[i];
			var tabItem = tabList.appendChild(document.createElement("li"));
			if(tabView.active) {
				tabItem.className = "active";
			}
			var tabItemLink = tabItem.appendChild(document.createElement("a"));
			tabItemLink.setAttribute("data-toggle","tab");
			tabItemLink.setAttribute("href","#"+viewDefinition.id+"_"+tabView.key);
			tabItemLink.innerHTML = tabView.title;
		}
		var tabContainerBody = tab.appendChild(document.createElement("div"));
		tabContainerBody.className = "tab-content";
		for(var i = 0; i < viewDefinition.tabs.length; i++) {
			var tabView = viewDefinition.tabs[i];
			var tabBody = tabContainerBody.appendChild(document.createElement("div"));
			tabBody.className = "tab-pane"+(tabView.active ? " active" : "");
			tabBody.id = viewDefinition.id+"_"+tabView.key;
			var innerTabBody = tabBody.appendChild(document.createElement("div"));
			innerTabBody.className = "panel-body";
			CX2.buildComponent(tabView.view,innerTabBody);
		}
		return tabContainer;
	},
	binary: function(viewDefinition) {
		var binaryElm = document.createElement("div");
		binaryElm.className = "fileinput fileinput-new input-group";
		binaryElm.setAttribute("data-provides","fileinput");
		var binaryElmControl = binaryElm.appendChild(document.createElement("div"));
		binaryElmControl.className = "form-control";
		binaryElmControl.setAttribute("data-trigger","fileinput");
		var binaryElmControlIcon = binaryElmControl.appendChild(document.createElement("i"));
		binaryElmControlIcon.className = "glyphicon glyphicon-file fileinput-exists";
		var binaryElmControlItem = binaryElmControl.appendChild(document.createElement("span"));
		binaryElmControlItem.className = "fileinput-filename";
		var binaryElmInputContainer = binaryElm.appendChild(document.createElement("span"));
		binaryElmInputContainer.className = "input-group-addon btn btn-default btn-file";
		var binaryElmInputNew = binaryElmInputContainer.appendChild(document.createElement("span"));
		binaryElmInputNew.className = "fileinput-new";
		binaryElmInputNew.innerHTML = "Select file";
		var binaryElmInputChange = binaryElmInputContainer.appendChild(document.createElement("span"));
		binaryElmInputChange.className = "fileinput-exists";
		binaryElmInputChange.innerHTML = "Change";
		var binaryElmInput = binaryElmInputContainer.appendChild(document.createElement("input"));
		binaryElmInput.setAttribute("type","file");
		binaryElmInput.setAttribute("name",viewDefinition.name);
		var binaryElmRemove = binaryElm.appendChild(document.createElement("a"));
		binaryElmRemove.setAttribute("href","#");
		binaryElmRemove.className = "input-group-addon btn btn-default fileinput-exists";
		binaryElmRemove.setAttribute("data-dismiss","fileinput");
		binaryElmRemove.innerHTML = "Remove";
		return binaryElm;
	},
	readonly: function(viewDefinition) {
		var readonlyElm = document.createElement("p");
		readonlyElm.className = "form-control-static";
		readonlyElm.innerHTML = viewDefinition.value;
		
		return readonlyElm;
	},
	colorpicker: function(viewDefinition) {
		/*<h5>As normal input</h5>
			<input type="text" class="form-control demo1 colorpicker-element" value="#5367ce">
			<h5>As a link</h5>
			<a data-color="rgb(255, 255, 255)" id="demo_apidemo" class="btn btn-white btn-block colorpicker-element" href="#">Change background color</a>*/
		var colorPicker = document.createElement("div");
		colorPicker.className = "input-group colorpicker-component";
		var colorPickerLinkContainer = colorPicker.appendChild(document.createElement("a"));
		colorPickerLinkContainer.className = "input-group-addon";
		colorPickerLinkContainer.innerHTML = "<i class=\"fa fa-eyedropper\"></i>";
		var colorPickerInput = colorPicker.appendChild(document.createElement("input"));
		colorPickerInput.setAttribute("type","text");
		colorPickerInput.setAttribute("name",viewDefinition.name);
		colorPickerInput.className = "form-control";
		if(viewDefinition.value) {
			colorPickerInput.setAttribute("value",viewDefinition.value);
		}
		
		return colorPicker;
	},
	htmleditor: function(viewDefinition) {
		//we need to substitute summernote with ckeditor
		var htmleditor = document.createElement("textarea");
		htmleditor.id = CX2.util.UUID();
		htmleditor.name = viewDefinition.name;
		htmleditor.className = "ckeditor";
		if(viewDefinition.height) {
			htmleditor.setAttribute("x2_height", viewDefinition.height);
		}
		htmleditor.innerHTML = viewDefinition.value ? viewDefinition.value : "";
		return htmleditor;
	},
	checkboxgroup: function(viewDefinition) {
		var cbgroup = document.createElement("div");
		for(var i = 0; i < viewDefinition.values.length; i++) {
			var item = viewDefinition.values[i];
			var cbItem = cbgroup.appendChild(document.createElement("div"));
			var cbItemLabel = cbItem.appendChild(document.createElement("label"));
			var cb = cbItemLabel.appendChild(document.createElement("input"));
			cb.setAttribute("type","checkbox");
			cb.setAttribute("name",viewDefinition.name);
			cb.setAttribute("value",item.key);
			for(var j = 0; j < viewDefinition.checked.length; j++) {
				if(viewDefinition.checked[j] === item.key) {
					cb.checked = true;
					break;
				}
			}
			var spTitleElm = cbItemLabel.appendChild(document.createElement("span"));
			spTitleElm.style.marginLeft = "0.5em";
			spTitleElm.innerHTML = item.value;
		}
		return cbgroup;
	},
	date: function(viewDefinition) {
		var formCompElm = document.createElement("input");
		formCompElm.type = "date";
		formCompElm.name = viewDefinition.name;
		formCompElm.className = "form-control";
		if(viewDefinition.value) {
			formCompElm.value = viewDefinition.value;
		}
		
		return formCompElm;
	},
	time: function(viewDefinition) {
		var formCompElm = document.createElement("input");
		formCompElm.type = "time";
		formCompElm.name = viewDefinition.name;
		formCompElm.className = "form-control";
		if(viewDefinition.value) {
			formCompElm.value = viewDefinition.value;
		}
		
		return formCompElm;
	},
	datetime: function(viewDefinition) {
		var formCompElm = document.createElement("input");
		formCompElm.type = "datetime-local";
		formCompElm.name = viewDefinition.name;
		formCompElm.className = "form-control";
		if(viewDefinition.value) {
			formCompElm.value = viewDefinition.value;
		}
		
		return formCompElm;
	}
};

CX2.runEvent = function(event,target,payload,onSuccess) {
	if(event === "onListFeatures") {
		CX2.runOnBackend("onListFeatures",function(data,status,xhr) { CX2.components.application_menu(data); },CX2.handleBackendError);
	} else {
		if(target && String(target).startsWith("javascript:")) {
			CX2.invokeHandler({
				data: {
					target: target
				}
			});
		} else {
			CX2.runOnBackend(event,onSuccess,CX2.handleBackendError,document.getElementById(target),JSON.stringify(payload),"application/json");
		}
	}
};

CX2.code = {
	components : {
		datalist: {
			controller: null,
			save : function(dialogId) {
				//now the save function needs to all the backend component to create a form view for this new item, the form view
				//will be a combination of the standard associated view and the data which will be posted back to the form.
				var dialog = document.getElementById(dialogId);
				var view = dialog.viewDefinition;
				var form = $(dialog).find("form").first().get(0);
				var payloadData = new FormData(form);
				payloadData.append("cx2.sys.view",JSON.stringify(view));
				$(form).find("textarea").each(function(index,element) {
					if(element.name && element.name.length > 0) {
						payloadData.append(element.name,$(element).val());
					}
				});
				CX2.runOnBackend("action/addnew/"+CX2.code.components.datalist.controller,function(data,status,xhr) {
					//we expect the returning component to give us a DataListItem which we will then add to the overall component list.
					//first lets hide the dialog
					data.selectedDataListId = this.selectedDataListId;
					data.dataListId = this.dataListId;
					data.view = this.viewDefinition;
					if(this.itemId) {
						var itemElm = document.getElementById(this.itemId);
						CX2.code.components.datalist.setItem(data,itemElm);
					} else {
						CX2.code.components.datalist.addItem(data,document.getElementById(this.selectedDataListId),this.viewDefinition.allowEdit);
					}
					var dataList = document.getElementById(this.dataListId);
					//we need to set the selected key back to the first item
					dataList.selectedIndex = 0;
					$(this).modal('hide');
					CX2.code.components.datalist.sync(data.selectedDataListId);
				},CX2.handleBackendError,dialog,payloadData,false);
			},
			setItem : function(item,itemElm) {
				itemElm.innerHTML = "<h3>"+item.key+"</h3> "+item.title;
				itemElm.item = item;
			},
			addItem : function(item,selectedDataList,allowEdit) {
				var newItem = selectedDataList.appendChild(document.createElement("div"));
				newItem.className = "well well-sm";
				newItem.innerHTML = "<h3>"+item.key+"</h3> "+item.title;
				newItem.item = item;
				newItem.id = CX2.util.UUID();
				if(allowEdit) {
					$(newItem).on('click',{ itemElm: newItem },function(event) {
						CX2.code.components.datalist.showEditDialog(event.data.itemElm.item,event.data.itemElm.id);
					});
				}
			},
			remove: function(dialogId,itemId) {
				if(confirm('Are you sure you want to remove the item?')) {
					var dialog = document.getElementById(dialogId);
					//we need to add this item as an option to the list of things which can be selected
					var itemElm = document.getElementById(itemId);
					var item = itemElm.item;
					var dataList = document.getElementById(item.dataListId);
					var optionElm = document.createElement("option");
					optionElm.setAttribute("value",item.key);
					optionElm.innerHTML = item.title;
					optionElm.item = item;
					dataList.appendChild(optionElm);
					$(itemElm).remove();
					$(dialog).modal('hide');
					CX2.code.components.datalist.sync(item.selectedDataListId);
				}
			},
			sync: function(selectedDataListId) {
				//this method should actually save the value of all the selected items to some json which is serialised 
				//within a hidden field. this field will contain the json for all of the selected items which will contain
				//a reference to the list item controller for de-serialisation and the rest should be an array which contains
				//the different list items.
				var selectedDataList = document.getElementById(selectedDataListId);
				var fieldValueElm = document.getElementById("value_"+selectedDataListId);
				//the selected data list will have a list of values
				var itemValueList = new Array();
				var itemList = selectedDataList.getElementsByTagName("div");
				for(var i = 0; i < itemList.length; i++) {
					itemValueList.push(itemList[i].item);
				}
				fieldValueElm.value = JSON.stringify(itemValueList);
			},
			showEditDialog : function(item,itemId) {
				var uuid = CX2.util.UUID();
				item.dataForm.buttons = [
					{
						title: "Save",
						target: "javascript:CX2.code.components.datalist.save('"+uuid+"');"
					},
					{
						title: "Cancel",
						target: "javascript:$('#"+uuid+"').modal('hide');"
					}
				];
				if(itemId) {
					item.dataForm.buttons.push({
							title: "Remove from List",
							target: "javascript:CX2.code.components.datalist.remove('"+uuid+"','"+itemId+"');"
					});
				}
				var dialog = CX2.components.dialog({ components: [ item.dataForm ] });
				dialog.id = uuid;
				dialog.viewDefinition = item.view;
				dialog.selectedDataListId = item.selectedDataListId;
				dialog.dataListId = item.dataListId;
				if(itemId) {
					dialog.itemId = itemId;
				}
				$(dialog).modal(); //display the dialog in the context of the view.
				$(dialog).on('hidden.bs.modal',dialog,function() {
					$(this).remove();
				});
			}
		}
	}
};

if(window.x2_authentication) {
	CX2.auth = {
		signIn : function(data) {
			CX2.user = data;
			document.getElementById("feature-title").innerHTML = "Welcome to "+window.x2_app.title;
			document.getElementById("feature-body").innerHTML = "";
			//we also need to add an onUserLogin function, which the application can use to manage users at it wishes, as soon
			//as the authentication mechanism is complete the application will become aware of the user scenario and can then authenticate
			//accordingly.
			CX2.runEvent("onUserLogin","feature-body",JSON.stringify(CX2.user),function(data) {
				if(data.message) {
					document.getElementById("feature-body").innerHTML = "<h1>"+data.message+"</h1>";
				}
				CX2.runEvent("onListFeatures");
				CX2.runEvent("onTopMenu","application-menu",null,CX2.runAction);
			});
		}
	};
	CX2.auth.google = {
		gAuth: null,
		init: function() {
			gapi.client.init({
					'apiKey': encodeURIComponent(window.x2_app.google_api_key),
					'clientId': encodeURIComponent(window.x2_app.google_client_id),
					'scope': encodeURIComponent('profile'),
					'discoveryDocs': ['https://people.googleapis.com/$discovery/rest?version=v1']
			}).then(function () {
					CX2.auth.google.gAuth = gapi.auth2.getAuthInstance();
					//console.log("response from google -> GoogleAuth : "+JSON.stringify(GoogleAuth));
					// Listen for sign-in state changes.
					CX2.auth.google.gAuth.isSignedIn.listen(CX2.auth.google.loginListen);
					CX2.auth.google.gAuth.currentUser.listen(CX2.auth.google.loginListen);
					var user = CX2.auth.google.gAuth.currentUser.get();
					if(user && !user.hasGrantedScopes('profile')) {
						document.getElementById('google_switch_user_auth').style.display = 'none';
					}
					//CX2.auth.google.gAuth.attachClickHandler('click',function() { console.log('authentication successfull'); }, function() { console.log('authentication failed'); });
					//currentApiGRequest = document.location.pathname;
			});
		},
		loginListen: function() {
			CX2.auth.google.login(true);
		},
		login: function(keepCurrentUser) {
			var user = CX2.auth.google.gAuth.currentUser.get();
			if(user) {
				$('#x2_auth_container').css('display','none');
				if(user.hasGrantedScopes('profile') && keepCurrentUser) {
					CX2.user_token = user.getAuthResponse(true).access_token;
					//at this point we know that the user authentication was successful.
					var profileData = user.getBasicProfile();
					CX2.auth.signIn({
						id : profileData.getEmail(),
						givenName : profileData.getGivenName(),
						surname :  profileData.getFamilyName(),
						userPrincipalName : profileData.getEmail(),
						access_token: CX2.user_token
					});
				} else {
					if(CX2.auth.google.gAuth.isSignedIn) {
						CX2.auth.google.gAuth.signOut().then(function() {
							CX2.auth.google.gAuth.signIn({ prompt : 'select_account' });
						});
					} else {
						CX2.auth.google.gAuth.signIn();
					}
				}
			} else {
				console.log('The current authenticated user is null');
			}
		}
	};
	/*onGoogleSignIn = function(googleUser) {
		var profile = googleUser.getBasicProfile();
		console.log(JSON.stringify(profile));
	};*/
	//authentication is required for this application so we need to check if the user is authenticated before proceeding.
	var authCode = CX2.util.getUrlParameter("code");
	if(authCode) {
		CX2.baseUrl = document.location.href.substr(0,document.location.href.indexOf('?'));
		$.ajax({
			cache: false,
			dataType: "json",
			method: "GET",
			processData: false,
			url: (document.location.pathname === "/" ? "" : document.location.pathname)+"/authentication/auth?code="+encodeURIComponent(authCode)+"&redirect_uri="+encodeURIComponent(CX2.baseUrl),
			error: CX2.handleBackendError,
			success: function(data) {
				CX2.user_token = data.access_token;
				//we now need to call microsoft graph to get information about the user.
				$.ajax({
					cache: true,
					dataType: "json",
					method: "GET",
					processData: false,
					url: "https://graph.microsoft.com/v1.0/me",
					beforeSend: function(xhr){ xhr.setRequestHeader("Authorization", "Bearer "+CX2.user_token);},
					error: function(xhr,status,error) {
						if(error !== "Unauthorized") {
							CX2.handleBackendError(xhr,status,error);
						} else {
							document.location.href=document.location.pathname;
						}
					},
					success: CX2.auth.signIn
				});
			}
		});
	}
	if(CX2.user_token) { //the variable may come from a cookie in the future
		CX2.runEvent("onListFeatures");
		CX2.runEvent("onTopMenu","application-menu",null,CX2.runAction);
	} else {
		//we expect this to run on https always otherwise it will not work
		if(document.location.protocol !== "https:") {
			var newUrl = "https://"+document.location.hostname;
			if(document.location.hostname === "localhost") {
				newUrl += ":8080";
			}
			newUrl += document.location.pathname;
			document.location.href = newUrl;
		}
		var msAuthURL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id="+encodeURIComponent(window.x2_app.microsoft_key)
									+ "&redirect_uri="+encodeURIComponent(document.location.href.substring(0,document.location.href.indexOf('?') > 0 ? document.location.href.indexOf('?') : document.location.href.length))+"&scope="+encodeURIComponent("User.Read")+"&response_type=code";
					
		document.getElementById("feature-title").innerHTML = window.x2_app.title+" - Login";
		document.getElementById("feature-body").innerHTML = "\n<div class=\"col-lg-12\" id=\"x2_auth_container\" style=\"display: none;\">\n\
		<div class=\"col-lg-12\"><h3>Choose your authentication provider</h3></div>\n\
		<div class=\"col-lg-12\">\n\
			<div class=\"col-lg-2\"></div>\n\
			<div class=\"col-lg-4\">\n\
				<a href=\""+msAuthURL+"\" target=\"_self\" id=\"x2_msauth_link\"><img width=\"150\" src=\""+(document.location.pathname === "/" ? "" : document.location.pathname)+"/microsoft_login_logo.png\"></img><br/><b>Login with Microsoft</b></a>\n\
			</div>\n\
			<div class=\"col-lg-4\">\n\
				<a href=\"javascript:CX2.auth.google.login(true)\" target=\"_self\" id=\"x2_googleauth_link\"><img width=\"150\" src=\""+(document.location.pathname === "/" ? "" : document.location.pathname)+"/google_login_logo.jpg\"></img><br/><b>Login with Google</b></a>\n\
				<div id=\"google_switch_user_auth\"><a style=\"font-weight: bold;\" href=\"javascript:CX2.auth.google.login(false)\" target=\"_self\">Switch Google User</a></div>\n\
			</div>\n\
			<div class=\"col-lg-2\"></div>\n\
		</div></div>";
		$('#x2_msauth_link').click(function() {
			$('#x2_auth_container').css('display','none');
		});
	}
} else {
	CX2.runEvent("onListFeatures");
	CX2.runEvent("onTopMenu","application-menu",null,CX2.runAction);
}
$(document).keypress(
    function(event){
     if (event.which == '13') {
        event.preventDefault();
     }
});

CX2.htmlEditors = [];
CX2.updateEditors = function() {
	for(var i = 0; i < CX2.htmlEditors.length; i++) {
		CX2.htmlEditors[i].updateElement();
	}
};
CKEDITOR.on("instanceReady",function(evt) {
	CX2.htmlEditors[CX2.htmlEditors.length] = evt.editor;
});

CKEDITOR.on("instanceDestroyed",function(evt) {
	var newEditorList = [];
	for(var i = 0; i < CX2.htmlEditors.length; i++) {
		if(CX2.htmlEditors[i].id !== evt.editor.id) {
			newEditorList[newEditorList.length] = CX2.htmlEditors[i];
		}
		CX2.htmlEditors = newEditorList;
	}
});

if(document.location.search.length === 0) { 
	$(document).ready(function() {
		$('#x2_auth_container').css('display','block');
	});
}

