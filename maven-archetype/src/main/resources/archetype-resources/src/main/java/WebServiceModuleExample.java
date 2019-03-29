package ${package};

import org.eclipse.jemo.api.WebServiceModule;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WebServiceModuleExample implements WebServiceModule {

    @Override
    public String getBasePath() {
        // TODO: change the endpoint
        return "/my-endpoint";
    }

    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        // TODO: add code to serve HTTP requests
    }

}