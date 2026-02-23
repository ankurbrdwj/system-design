package webcurve.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import quickfix.ListenerSupport;

public class MultiSubscriptionManager<K, T> {

	Map<String, ArrayList<ExchangeEventListener<T>>> subscriptions = Collections.synchronizedMap(new HashMap<String, ArrayList<ExchangeEventListener<T>>>());
	
	String getStringKey(K key, Class<T> type)
	{
		return type.toString() + "-" + key.toString();
	}
	
	public boolean subscribe(K key, Class<T> type, ExchangeEventListener<T> listener)
	{
		ArrayList<ExchangeEventListener<T>> listeners = subscriptions.get(getStringKey(key, type));
		if ( null == listeners)
		{
			listeners = new ArrayList<ExchangeEventListener<T>>();
			subscriptions.put(getStringKey(key, type), listeners);
		}
		else if (listeners.contains(listener))
				return false;
	
		listeners.add(listener);
		return true;
		
	}

	public boolean unsubscribe(K key, Class<T> type, ExchangeEventListener<T> listener)
	{
		ArrayList<ExchangeEventListener<T>> listeners = subscriptions.get(getStringKey(key, type));
		if ( null == listeners)
			return false;
		
		return listeners.remove(listener);
	}
	
	
	public void update(K key, Class<T> type, T t)
	{
		ArrayList<ExchangeEventListener<T>> listeners = subscriptions.get(getStringKey(key, type));
		if ( null == listeners)
			return;
		
		for (ExchangeEventListener<T> listener: listeners)
		{
			listener.onChangeEvent(t);
		}
		
	}
}
