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
package org.eclipse.jemo.ui.view.form;

import org.eclipse.jemo.ui.view.FormComponentView;
import org.eclipse.jemo.ui.view.FormView;

import java.util.Map;

/**
 * @author christopher stura
 */
public class KeyPairDataListView extends DataListView<KeyPairDataListView.KeyPairDataListItem> {

    private static final String keyFormField = "item_key";
    private static final String titleFormField = "item_title";

    public KeyPairDataListView(String name, KeyPairDataListItem... items) {
        super(name, KeyPairDataListItem.class, items);

        setDataItemView(new FormView().addComponent(new TextView("item_key").setTitle("Item Key")).addComponent(new TextView("item_title").setTitle("Item Title")));
        setItemLabel("Option");
    }

    public KeyPairDataListView() {
    }

    public static class KeyPairDataListItem implements DataListView.DataListItem {

        private String key = null;
        private String title = null;
        private FormView view = null;

        public KeyPairDataListItem() {
        }

        public KeyPairDataListItem(String key, String title) {
            this.key = key;
            this.title = title;
        }

        public String getKey() {
            return this.key;
        }

        public String getTitle() {
            return this.title;
        }

        public FormView getDataForm() {
            return this.view;
        }

        public void setValueOnView(FormComponentView view) {
            switch (view.getName()) {
                case keyFormField:
                    view.setValue(getKey());
                    break;
                case titleFormField:
                    view.setValue(getTitle());
                    break;
            }
        }

        public void initialize(DataListView view, Map<String, Object> formData) {
            if (formData.containsKey(keyFormField)) {
                this.key = formData.get(keyFormField).toString();
            }
            if (formData.containsKey(titleFormField)) {
                this.title = formData.get(titleFormField).toString();
            }
            this.view = view.getDataItemView();
        }
    }

    @Override
    public String getCls() {
        return DataListView.class.getSimpleName();
    }


}
