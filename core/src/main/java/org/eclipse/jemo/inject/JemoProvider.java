package org.eclipse.jemo.inject;

import javax.inject.Provider;

public class JemoProvider<T extends Object> implements Provider<T> {

	private T value;
	
	public JemoProvider(T value) {
		this.value = value;
	}
	
	@Override
	public T get() {
		return value;
	}
	
	public void set(T value) {
		this.value = value;
	}
	
}
