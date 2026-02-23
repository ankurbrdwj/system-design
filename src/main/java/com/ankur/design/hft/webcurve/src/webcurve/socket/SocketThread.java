package webcurve.socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.Socket;
/**
 * @author dennis_d_chen@yahoo.com
 */
public class SocketThread extends Thread implements Runnable {
	static Logger  log = LoggerFactory.getLogger(SocketThread.class);

	protected SocketHandler handler;
	protected Socket socket;
	
	public SocketThread(Socket socket, SocketHandler handler)
	{
		this.handler = handler;
		this.socket = socket;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try
		{
			while ( true )
			{
				if (!socket.isConnected())
				{
					log.info("socket disconnected");
					break;
				}
				if ( !handler.handleSocket(socket))
				{
					log.info("server released client socket");
					socket.close();
					break;
				}

			}
		}
		catch (Exception ex)
		{
			log.warn(ex.toString());
		}

	}

}
