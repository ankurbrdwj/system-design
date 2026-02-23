package webcurve.ui;

import java.util.Vector;

import javax.swing.JTree;
import javax.swing.event.EventListenerList;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import webcurve.client.*;

/**
 * @author dennis_d_chen@yahoo.com
 */
public class TreeTableModel extends AbstractTableModel implements TreeModel {
	static String[] columnNames = {"OrderId", "Stock", "Side", "Quantity", 
									"Type", "Price", "Left", "Status"};
    static protected Class[]  columnClass = {TreeTableModel.class, String.class, String.class, String.class, 
    										String.class, String.class, String.class, String.class};
	
    private ClientOrder root;
	private Vector<ClientOrder> orders;
	JTree tree;
    public JTree getTree() {
		return tree;
	}

	public void setTree(JTree tree) {
		this.tree = tree;
		tree.addTreeExpansionListener(new TreeExpansionListener() {
		    // Don't use fireTableRowsInserted() here; 
		    // the selection model would get  updated twice. 
		    public void treeExpanded(TreeExpansionEvent event) {  
		      fireTableDataChanged(); 
		    }
	        public void treeCollapsed(TreeExpansionEvent event) {  
		      fireTableDataChanged(); 
		    }
		});		
	}
	
	public TreeTableModel(Vector<ClientOrder> orders)
	{
		this.orders = orders;
		root = new ClientOrder();
		root.setClientOrderID("");
		root.setChildOrders(orders);
	}
	
	@Override
	public Class<?> getColumnClass(int col)
	{
		return columnClass[col];
	}

	@Override
	public String getColumnName(int col)
	{
		return columnNames[col];
	}

	
//	@Override
	public Object getChild(Object parent, int index) {
		// TODO Auto-generated method stub
		return ((ClientOrder)parent).getChildOrders().get(index);
	}

//	@Override
	public int getChildCount(Object parent) {
		// TODO Auto-generated method stub
		return ((ClientOrder)parent).getChildOrders().size();
	}

//	@Override
	public int getIndexOfChild(Object parent, Object child) {
		// TODO Auto-generated method stub
		ClientOrder order = (ClientOrder)parent;
		for (int i=0; i<order.getChildOrders().size(); i++)
		{
			if (child == order.getChildOrders().get(i))
				return i;
		}
		return -1;
	}

//	@Override
	public Object getRoot() {
		// TODO Auto-generated method stub
		return root;
	}

//	@Override
	public boolean isLeaf(Object node) {
		// TODO Auto-generated method stub
		return ((ClientOrder)node).getChildOrders().size()==0;
	}

    protected EventListenerList listenerList = new EventListenerList();
//	@Override
	public void addTreeModelListener(TreeModelListener l) {
		// TODO Auto-generated method stub
        listenerList.add(TreeModelListener.class, l);
	}

//	@Override
	public void removeTreeModelListener(TreeModelListener l) {
		// TODO Auto-generated method stub
        listenerList.remove(TreeModelListener.class, l);
	}

//	@Override
	public void valueForPathChanged(TreePath arg0, Object arg1) {
		// TODO Auto-generated method stub

	}

//	@Override
	public int getColumnCount() {
		// TODO Auto-generated method stub
		return columnNames.length;
	}

//	@Override
	public int getRowCount() {
		// TODO Auto-generated method stub
		return tree.getRowCount();
	}
//	@Override
	public Object getValueAt(int row, int col) {
		// TODO Auto-generated method stub
		TreePath path = tree.getPathForRow(row);
		ClientOrder order = (ClientOrder)path.getLastPathComponent();     
		switch (col)
		{
		case 0: return order.getClientOrderID();
		case 1: return order.getCode();
		case 2: return ClientOrder.sideToString(order.getSide(), order.isShortSell());
		case 3: return order.getQuantity();
		case 4: return ClientOrder.typeToString(order.getType());
		case 5: return order.getPrice();
		case 6: return order.getLeftQuantity();
		case 7: return ClientOrder.statusToString(order.getStatus());
		}
		
		System.out.println("return nothing!!!");
		return null;
	}
	
	public Object getOrderAt(int row)
	{
		TreePath path = tree.getPathForRow(row);
		return path.getLastPathComponent();     
	}
	
    public boolean isCellEditable(int row, int column) {
        return column == 0; 
   }

}
