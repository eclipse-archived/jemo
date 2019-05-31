package ${package};

import org.eclipse.jemo.api.WebServiceModule;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// For more info on how to implement this class plese read https://www.eclipse.org/jemo/docs.php
// An example can be found in https://github.com/eclipse/jemo/blob/master/demos/jemo-trader-app/src/main/java/org/eclipse/jemo/tutorial/market/Market.java
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