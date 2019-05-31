package ${package};

import org.eclipse.jemo.api.EventModule;
import org.eclipse.jemo.internal.model.JemoMessage;

import java.io.IOException;

// For more info on how to implement this class plese read https://www.eclipse.org/jemo/docs.php
// An example can be found in https://github.com/eclipse/jemo/blob/master/demos/jemo-trader-app/src/main/java/org/eclipse/jemo/tutorial/market/MarketMatcher.java
public class EventModuleExample implements EventModule {

    @Override
    public JemoMessage process(JemoMessage msg) throws IOException {
        // TODO: Add code to consume the message

        return null;
    }

}