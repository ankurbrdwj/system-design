package webcurve.test;

import webcurve.ui.TradeJFrame;

public class Test4 {
	public static String cfgFile = "";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
        Test4.cfgFile = "";
        if (args.length>0)
        	Test4.cfgFile = args[0];

        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new TradeJFrame(Test4.cfgFile).setVisible(true);
            }
        });

	}

}
