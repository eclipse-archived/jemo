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

import org.eclipse.jemo.ui.util.Util;

import static org.eclipse.jemo.ui.util.Util.S;

import org.eclipse.jemo.ui.view.ButtonView;
import org.eclipse.jemo.ui.view.View;
import org.eclipse.jemo.ui.view.form.DateTimeView;
import org.eclipse.jemo.ui.view.form.DateView;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import javax.servlet.http.Part;

/**
 * this class will define the backend binding for a ui button.
 * it will respond to all events that may be triggered on a button.
 *
 * @author christopher stura
 */
public abstract class Button extends Component {

    protected static Pattern DATE_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    protected HashMap<String, List<Part>> formData = new HashMap<>();

    /**
     * this method will return a view representing what will need to be drawn after the button operation has completed.
     *
     * @param view the button view
     * @return the view returned after the button operation has completed.
     * @throws Throwable in an event of a failure
     */
    public abstract View onClick(ButtonView view) throws Throwable;

    public void addFormPart(String fieldName, Part fieldData) {
        List<Part> partList = formData.get(fieldName);
        if (partList == null) {
            partList = new ArrayList<>();
            formData.put(fieldName, partList);
        }
        partList.add(fieldData);
    }

    public String getFormFieldAsString(String fieldName) {
        List<Part> pList = formData.get(fieldName);
        if (pList != null && !pList.isEmpty()) {
            try {
                StringBuilder str = new StringBuilder();
                for (Part p : pList) {
                    str.append(str.length() == 0 ? "" : ",").append(Util.toString(p.getInputStream()));
                }

                return str.toString();
            } catch (IOException ioex) {
                return null;
            }
        }

        return null;
    }

    public List<String> getFormFieldAsStringList(String fieldName) {
        ArrayList<String> result = new ArrayList<>();
        List<Part> pList = formData.get(fieldName);
        if (pList != null && !pList.isEmpty()) {
            try {
                for (Part p : pList) {
                    result.add(Util.toString(p.getInputStream()));
                }
            } catch (IOException ioex) {
                return result;
            }
        }

        return result;
    }

    public int getFormFieldAsInteger(String fieldName) {
        String strValue = getFormFieldAsString(fieldName);
        if (strValue != null && strValue.matches("[0-9\\.\\,]+")) {
            return new Double(strValue).intValue();
        }

        return 0;
    }

    public boolean getFormFieldAsBoolean(String fieldName) {
        String strValue = S(getFormFieldAsString(fieldName));
        if (strValue.equalsIgnoreCase("true")) {
            return true;
        }

        return false;
    }

    public Calendar getFormFieldAsCalendar(String fieldName) throws ParseException {
        String strValue = S(getFormFieldAsString(fieldName));
        if (!strValue.trim().isEmpty()) {
            Calendar cal = Calendar.getInstance();
            if (DATE_PATTERN.matcher(strValue).matches()) {
                cal.setTimeInMillis(DateView.DATE_FORMAT.parse(strValue).getTime());
            } else {
                cal.setTimeInMillis(DateTimeView.DATETIME_FORMAT.parse(strValue).getTime());
            }

            return cal;
        }

        return null;
    }

    public Collection<String> listFormFields() {
        return formData.keySet();
    }

    public Part getFormPart(String fieldName) {
        List<Part> pList = formData.get(fieldName);
        if (pList != null && !pList.isEmpty()) {
            return pList.iterator().next();
        }
        return null;
    }
}
