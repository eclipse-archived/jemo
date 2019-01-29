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
package com.cloudreach.x2.ui.view.form;

import com.cloudreach.x2.ui.util.Util;
import com.cloudreach.x2.ui.Component;
import com.cloudreach.x2.ui.view.FormComponentView;
import com.cloudreach.x2.ui.view.FormView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * this view will describe a complex application that will allow the user to create an ordered list of abstract
 * data. Where the data to be sorted can be created on the fly or can be pulled from another source.
 * 
 * @author christopher stura
 * @param <T> the type of items that this data list view will be able to manage
 */
public class DataListView<T extends DataListView.DataListItem> extends FormComponentView<DataListView> {
	
	public static interface DataListItem {
		public String getKey();
		public String getTitle();
		public default FormView getDataForm() { return null; }
		
		@JsonIgnore
		public default void setValueOnView(FormComponentView view) {}
		
		@JsonIgnore
		public default void initialize(DataListView view,Map<String,Object> formData) {}
	}
	
	public static class DataListViewComponent extends Component {

		@Override
		protected Object handleEvent(String event, Map<String, Object> parameters, String payload) throws Throwable {
			if(event.equals("addnew")) {
				Map<String,Object> payloadMap = Util.fromJSON(Map.class, payload);
				Class implClass = Class.forName((String)payloadMap.get("implementation"));
				DataListView view = DataListView.class.cast(Util.fromJSON(implClass, payload));
				FormView form = view.getDataItemView();
				//now we need to grab the item class implementation from this view because we will need to use it to create a new instance for the data that was passed through the form.
				DataListItem item = DataListItem.class.cast(Class.forName(view.getItemController()).newInstance());
				item.initialize(view, parameters);
				form.getComponents().forEach(c -> item.setValueOnView(c));
				
				return item;
			}
			return super.handleEvent(event, parameters, payload); //To change body of generated methods, choose Tools | Templates.
		}
	}
	
	private List<T> items = new ArrayList<>();
	private FormView dataItemView = null;
	private boolean allowNew = false;
	private boolean allowEdit = false;
	private boolean allowDelete = false;
	private boolean allowSort = false;
	private String itemLabel = null;
	private String controller = DataListViewComponent.class.getName();
	private String itemController = null;
	
	public DataListView() { super("unknown"); } //this constructor will be used by de-serialisation 	
	public DataListView(String name,Class<T> itemProducer,T... items) {
		super(name);
		
		this.itemController = itemProducer.getName();
		this.items.addAll(Arrays.asList(items));
	}
	
	@JsonIgnore
	public DataListView setItemView(String itemLabel,FormView view,boolean allowNew,boolean allowEdit) {
		this.dataItemView = view;
		this.allowNew = allowNew;
		this.allowEdit = allowEdit;
		this.itemLabel = itemLabel;
		
		return this;
	}

	public String getItemLabel() {
		return itemLabel;
	}

	public void setItemLabel(String itemLabel) {
		this.itemLabel = itemLabel;
	}

	@JsonProperty(value = "items")
	public List<T> getItems() {
		return items;
	}

	@JsonIgnore
	public DataListView setItems(List<T> items) {
		this.items = items;
		
		return this;
	}

	@JsonProperty(value = "view")
	public FormView getDataItemView() {
		return dataItemView;
	}

	@JsonProperty(value = "view")
	public DataListView setDataItemView(FormView dataItemView) {
		this.dataItemView = dataItemView;
		
		return this;
	}

	public boolean isAllowDelete() {
		return allowDelete;
	}

	public DataListView setAllowDelete(boolean allowDelete) {
		this.allowDelete = allowDelete;
		
		return this;
	}

	public boolean isAllowSort() {
		return allowSort;
	}

	public DataListView setAllowSort(boolean allowSort) {
		this.allowSort = allowSort;
		
		return this;
	}

	public boolean isAllowNew() {
		return allowNew;
	}

	public DataListView setAllowNew(boolean allowNew) {
		this.allowNew = allowNew;
		
		return this;
	}

	public boolean isAllowEdit() {
		return allowEdit;
	}

	public DataListView setAllowEdit(boolean allowEdit) {
		this.allowEdit = allowEdit;
		
		return this;
	}

	public String getController() {
		return controller;
	}

	public String getItemController() {
		return itemController;
	}

	public DataListView setItemController(String itemController) {
		this.itemController = itemController;
		
		return this;
	}
	
	public String getImplementation() {
		return getClass().getName();
	}
}
