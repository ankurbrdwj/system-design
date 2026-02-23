package webcurve.test;

import webcurve.exchange.Exchange;
import webcurve.fix.ExchangeFixGateway;
import webcurve.ui.ExchangeJFrame;

public class Test3 {
	
	public static String cfgFile = "";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
        Test3.cfgFile = "";
        if (args.length>0)
        	Test3.cfgFile = args[0];
		
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
            	ExchangeJFrame frame = new ExchangeJFrame( new Exchange());
                frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
                
                ExchangeFixGateway fixGW = new ExchangeFixGateway(frame.getExchange());
        		if (!fixGW.open(Test3.cfgFile))
        		{
        			System.out.println("Error: Cant open FIX gateway");
        			return;
        		}
                frame.setVisible(true);
            }
        });
	}

}
