package ${package};

import org.eclipse.jemo.api.EventModule;
import org.eclipse.jemo.internal.model.JemoMessage;

import java.io.IOException;

public class EventModuleExample implements EventModule {

    @Override
    public JemoMessage process(JemoMessage msg) throws IOException {
        // TODO: Add code to consume the message

        return null;
    }

}