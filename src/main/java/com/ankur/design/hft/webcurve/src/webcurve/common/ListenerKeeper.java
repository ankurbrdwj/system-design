package webcurve.common;

import java.util.Vector;

/**
 * @author dennis_d_chen@yahoo.com
 */
public class ListenerKeeper<T>
{
	private Vector<ExchangeEventListener<T>> exchangeListeners = new Vector<ExchangeEventListener<T>>();
	
	public void addExchangeListener(ExchangeEventListener<T> listener)
	{
		if (null == listener)
			return;
		if (!exchangeListeners.contains(listener))
			exchangeListeners.add(listener);
	}

	public void removeExchangeListener(ExchangeEventListener<T> listener)
	{
		exchangeListeners.remove(listener);
	}
	
	public void updateExchangeListeners(T t)
	{
		for (ExchangeEventListener<T> item: exchangeListeners)
		{
			try
			{
				item.onChangeEvent(t);
			}
			catch(Exception e)
			{
				System.out.println(e);
			}
		}
	}
}