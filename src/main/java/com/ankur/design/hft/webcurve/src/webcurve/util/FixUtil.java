package webcurve.util;

import quickfix.field.Side;
import webcurve.client.*;
import webcurve.common.BaseOrder;
import webcurve.common.Order;
/**
 * @author dennis_d_chen@yahoo.com
 */
public class FixUtil {
	// Fix utils
	public static char toFixOrderSide(BaseOrder.SIDE side) throws Exception
	{
		if (side == BaseOrder.SIDE.BID)
			return '1';
		else if (side == BaseOrder.SIDE.ASK)
			return '2';
		else
			throw new Exception("toFixOrderSide: unknown side " + side);
	}	

	public static char toFixClientOrderSide(ClientOrder.SIDE side, boolean shortSell) throws Exception
	{
		if (side == ClientOrder.SIDE.BID)
			return '1';
		else if (side == ClientOrder.SIDE.ASK)
		{
			if (shortSell)
				return '5';
			else
				return '2';
		}
		else
			throw new Exception("toFixClientOrderSide: unknown side " + side);
	}	
	
	public static char toFixClientOrderType(ClientOrder.TYPE type) throws Exception
	{
		if (type == ClientOrder.TYPE.MARKET)
			return '1';
		else if (type == ClientOrder.TYPE.LIMIT)
			return '2';
		else
			throw new Exception("toFixClientOrderType: unknown type " + type);
	}	
	
	public static BaseOrder.SIDE fromFixOrderSide(char side) throws Exception
	{
    	if (side == '1')
    		return Order.SIDE.BID;
    	else if (side == '2' || side == '5')
    		return Order.SIDE.ASK;
    	else
			throw new Exception("fromFixOrderSide: unknown side " + side);
	}
}
