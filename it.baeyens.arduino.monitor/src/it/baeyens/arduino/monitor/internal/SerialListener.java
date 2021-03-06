package it.baeyens.arduino.monitor.internal;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.widgets.Display;

import it.baeyens.arduino.arduino.MessageConsumer;
import it.baeyens.arduino.common.Common;
import it.baeyens.arduino.common.Const;
import it.baeyens.arduino.monitor.views.Messages;
import it.baeyens.arduino.monitor.views.SerialMonitor;

public class SerialListener implements MessageConsumer {
    private static boolean myScopeFilterFlag = false;
    SerialMonitor TheMonitor;
    int theColorIndex;
    private ByteBuffer myReceivedScopeData = ByteBuffer.allocate(2000);

    public int removeBytesFromStart(int n) {
	if (n == 0) {
	    return this.myReceivedScopeData.position();
	}
	int index = 0;
	for (int i = 0; i < n; i++)
	    this.myReceivedScopeData.put(i, (byte) 0);
	for (int i = n; i < this.myReceivedScopeData.position(); i++) {
	    this.myReceivedScopeData.put(index++, this.myReceivedScopeData.get(i));
	    this.myReceivedScopeData.put(i, (byte) 0);
	}

	return this.myReceivedScopeData.position(index).position();
    }

    public SerialListener(SerialMonitor Monitor, int ColorIndex) {
	this.TheMonitor = Monitor;
	this.theColorIndex = ColorIndex;
	this.myReceivedScopeData.order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void message(byte[] newData) {
	if (myScopeFilterFlag) {
	    // filter scope data
	    try {
		this.myReceivedScopeData.put(newData);
	    } catch (BufferOverflowException e) {
		this.myReceivedScopeData.clear();
		Common.log(
			new Status(IStatus.WARNING, Const.CORE_PLUGIN_ID, Messages.SerialListener_scope_skipping_data));
	    }
	    internalExtractAndRemoveScopeData();
	} else {
	    // treat data just like a event
	    if (newData[newData.length - 1] == '\r') {
		newData[newData.length - 1] = ' ';
	    }
	    event(new String(newData));
	}
    }

    private void internalExtractAndRemoveScopeData() {

	String MonitorMessage = Const.EMPTY_STRING;
	boolean doneSearching = false;
	int length = this.myReceivedScopeData.position();
	// System.out.println(""); //$NON-NLS-1$
	// System.out.print("start :>"); //$NON-NLS-1$
	// System.out.print(new String(this.myReceivedScopeData.array()));
	// System.out.println("<"); //$NON-NLS-1$
	int searchPointer;
	for (searchPointer = 0; (searchPointer < length - 1) && !doneSearching; searchPointer++) {
	    if (this.myReceivedScopeData.getShort(searchPointer) != Const.SCOPE_START_DATA) {
		char addChar = (char) this.myReceivedScopeData.get(searchPointer);
		MonitorMessage += Character.toString(addChar);
	    } else {
		// have we received the full header of the scope data?
		if (length < (searchPointer + 6)) {
		    if (searchPointer != 0) {
			length = removeBytesFromStart(searchPointer);
		    }
		    doneSearching = true;
		    // System.out.println("case 1"); //$NON-NLS-1$
		} else {
		    int bytestoRead = this.myReceivedScopeData.getShort(2);
		    if ((bytestoRead < 0) || (bytestoRead > (10 * 2))) {
			Common.log(new Status(IStatus.WARNING, Const.CORE_PLUGIN_ID,
				Messages.SerialListener_error_input_part_1 + bytestoRead / 2
					+ Messages.SerialListener_error_input_part_2));
			searchPointer += 4;// skip the scope start and length so
					   // data is shown
			// System.out.println("case 2"); //$NON-NLS-1$
		    } else {
			if ((searchPointer + (bytestoRead + 4)) < length) {
			    searchPointer += (bytestoRead + 4) - 1; // just skip
								    // the
								    // data
			    // System.out.println("case 3"); //$NON-NLS-1$
			} else // not all data arrived for the latest data set
			{
			    if (searchPointer != 0) {
				length = removeBytesFromStart(searchPointer);
			    }
			    doneSearching = true;

			    // System.out.println("case 4"); //$NON-NLS-1$
			}
		    }
		}
	    }
	}
	if (!doneSearching) {
	    if (searchPointer == length - 1) {

		byte addChar = this.myReceivedScopeData.get(searchPointer);

		if (addChar != (byte) (Const.SCOPE_START_DATA)) {
		    if (addChar == '\r') {
			addChar = ' ';
		    }
		    searchPointer++;
		    MonitorMessage += Character.toString((char) addChar);
		}
	    }
	    removeBytesFromStart(searchPointer);
	}

	// System.out.print("done :"); //$NON-NLS-1$
	// System.out.print(" :>"); //$NON-NLS-1$
	// System.out.print(MonitorMessage);
	// System.out.println("<"); //$NON-NLS-1$

	event(MonitorMessage);
    }

    @Override
    public void dispose() {
	// No need to dispose something
    }

    @Override
    public void event(String event) {
	final String TempString = new String(event);
	Display.getDefault().asyncExec(new Runnable() {
	    @Override
	    public void run() {
		try {
		    SerialListener.this.TheMonitor.ReportSerialActivity(TempString, SerialListener.this.theColorIndex);
		} catch (Exception e) {// ignore as we get errors when closing
				       // down
		}
	    }
	});

    }

    public static void setScopeFilter(boolean selection) {
	myScopeFilterFlag = selection;

    }
}
