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
package org.eclipse.jemo.ui;

import org.eclipse.jemo.ui.view.AreaView;
import org.eclipse.jemo.ui.view.ButtonView;
import org.eclipse.jemo.ui.view.DialogView;
import org.eclipse.jemo.ui.view.LabelView;
import org.eclipse.jemo.ui.view.NoTargetButtonView;
import org.eclipse.jemo.ui.view.RowView;
import org.eclipse.jemo.ui.view.View;

/**
 * This compound component will generically let the user confirm an operation before it is executed.
 * The UI module will essentially display a dialog with a message and let the user confirm by selecting
 * a yes or no option, where no will simply close the dialog and yes instead will execute the forwarded action.
 *
 * @author christopher stura
 */
public class ConfirmDialog extends ButtonView {
    public ConfirmDialog() {
    }

    public ConfirmDialog(String title, String target, Class<? extends Button> action) {
        super(title, null, BuildConfirmDialogAction.class);

        setAttribute("x2_confirm_dialog_target", target);
        setAttribute("x2_confirm_dialog_action", action.getName());
    }

    public ConfirmDialog setMessage(String message) {
        setAttribute("x2_confirm_dialog_message", message);

        return this;
    }

    public static class BuildConfirmDialogAction extends Button {

        @Override
        public View onClick(ButtonView view) throws Throwable {
            DialogView dialog = new DialogView(view.getTitle());
            dialog.addComponent(new RowView().addComponent(
                    new AreaView(12).setStyle("text-align: center;")
                            .addComponent(new LabelView(12, view.getAttributeAsString("x2_confirm_dialog_message") == null ? "Are you sure you want to continue?" : view.getAttributeAsString("x2_confirm_dialog_message")))
            ));
            dialog.addComponent(new RowView().addComponent(
                    new AreaView(12).setStyle("margin-top: 1em;padding-top: 1em; text-align: right;")
                            .addComponent(new ButtonView("Yes", view.getAttributeAsString("x2_confirm_dialog_target"),
                                    (Class<Button>) Class.forName(view.getAttributeAsString("x2_confirm_dialog_action")))
                                    .setStyle("margin-right: 1em;")
                                    .copyAttributesFrom(view))
                            .addComponent(new NoTargetButtonView("No"))
            ));
            return dialog;
        }

    }

    @Override
    public String getCls() {
        return ButtonView.class.getSimpleName();
    }


}
