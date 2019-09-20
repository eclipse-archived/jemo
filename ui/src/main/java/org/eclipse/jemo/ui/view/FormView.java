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
package org.eclipse.jemo.ui.view;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.eclipse.jemo.internal.model.JemoError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * this view will contain the necessary business logic for rapidly building form elements.
 * everything contained in a form will be a component in it's own right of course however
 * this will allow us to organize them in a logical format and give use a few accelerators
 * to make building forms easy.
 *
 * @author christopher stura
 */
@JsonDeserialize(using = FormView.FormViewDeserializer.class)
public class FormView extends View {

    public static class FormViewDeserializer extends StdDeserializer<FormView> {

        public FormViewDeserializer() {
            this(null);
        }

        public FormViewDeserializer(Class<?> cls) {
            super(cls);
        }

        @Override
        public FormView deserialize(JsonParser jp, DeserializationContext dc) throws IOException {
            JsonNode jNode = jp.getCodec().readTree(jp);
            FormView view = new FormView();
            view.setDescription(jNode.get("description") != null ? jNode.get("description").asText() : null);
            view.setInline(jNode.get("inline") != null ? jNode.get("inline").asBoolean() : false);
            view.setAttributes(Jackson.getObjectMapper().convertValue(jNode.get("attributes"), new TypeReference<Map<String, String>>() {
            }));
            view.setContainerId(jNode.get("containerid") != null ? jNode.get("containerid").asText() : null);
            view.setInfoMessage(jNode.get("info_message") != null ? jNode.get("info_message").asText() : null);
            view.setWarningMessage(jNode.get("warn_message") != null ? jNode.get("warn_message").asText() : null);
            jNode.get("components").elements().forEachRemaining((jc) -> {
                try {
                    Class formComponent = Class.forName("org.eclipse.jemo.ui.view.form." + jc.get("class").asText());
                    FormComponentView componentView = (FormComponentView) Jackson.getObjectMapper().convertValue(jc, formComponent);
                    view.getComponents().add(componentView);
                } catch (ClassNotFoundException ex) {
                    try {
                        Logger.getLogger(FormView.class.getSimpleName()).info("I was unable to de-serialise the component: " + Jackson.getObjectMapper().writeValueAsString(jc) + " because of the error: " + JemoError.toString(ex));
                    } catch (JsonProcessingException ex1) {
                    }
                }
            });
            return view;
        }

    }

    private String id = UUID.randomUUID().toString();
    private String description = null;
    private List<FormComponentView> components = new ArrayList<>();
    private List<ButtonView> buttons = new ArrayList<>();
    private boolean inline = false;

    public boolean isInline() {
        return inline;
    }

    public FormView setInline(boolean inline) {
        this.inline = inline;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public String getId() {
        return id;
    }

    @JsonIgnore
    public FormView setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<FormComponentView> getComponents() {
        return components;
    }

    public List<ButtonView> getButtons() {
        return buttons;
    }

    @JsonIgnore
    public FormView addComponent(FormComponentView componentView) {
        getComponents().add(componentView);
        return this;
    }

    @JsonIgnore
    public FormView addComponentIf(boolean condition, FormComponentView componentView) {
        if (condition) {
            addComponent(componentView);
        }
        return this;
    }

    @JsonIgnore
    public FormView addButton(ButtonView buttonView) {
        buttonView.setFormId(getId());
        getButtons().add(buttonView);
        return this;
    }

    @JsonIgnore
    public FormView addButtonIf(boolean condition, ButtonView buttonView) {
        if (condition) {
            addButton(buttonView);
        }

        return this;
    }
}
