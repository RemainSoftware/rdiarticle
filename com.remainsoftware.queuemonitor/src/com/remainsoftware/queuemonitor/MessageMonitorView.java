package com.remainsoftware.queuemonitor;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.rse.core.model.Host;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.printing.PrintDialog;
import org.eclipse.swt.printing.PrinterData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.MessageQueue;
import com.ibm.as400.access.QueuedMessage;
import com.ibm.etools.iseries.connectorservice.ToolboxConnectorService;

public class MessageMonitorView extends ViewPart implements ISelectionListener {

	private Text text;
	public MessageQueue queue;
	private QueuedMessage lastMessage;
	private Host currentHost;
	private Thread monitorThread;
	protected String user = "Me";
	protected boolean alert = true;
	protected int severityInt = 30;
	private IWorkbenchPart oldPart;
	private ISelection oldSelection;

	public MessageMonitorView() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void createPartControl(Composite parent) {

		parent.setLayout(new org.eclipse.swt.layout.GridLayout(1, false));

		final org.eclipse.swt.widgets.Combo combo = new Combo(parent,
				SWT.READ_ONLY | SWT.DROP_DOWN);
		combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		combo.setItems(new String[] { "Me", "QSYSOPR" });
		combo.setText("Me");
		combo.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				user = combo.getText();
				currentHost = null;
				selectionChanged(oldPart, oldSelection);
			}
		});

		Group group = new Group(parent, SWT.NONE);
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		group.setLayout(new GridLayout(2, false));

		final org.eclipse.swt.widgets.Button button = new Button(group,
				SWT.CHECK);
		button.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false,
				false));
		button
				.setText("Alert when new messages arrive with severity higher or equal to ");
		button.setSelection(true);
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				alert = button.getSelection();
				selectionChanged(oldPart, oldSelection);
			}
		});

		final Spinner severity = new Spinner(group, SWT.BORDER);
		severity.setMinimum(0);
		severity.setMaximum(90);
		severity.setSelection(30);
		severity.setIncrement(10);
		severity.setPageIncrement(10);
		severity.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				severityInt = severity.getSelection();
				selectionChanged(oldPart, oldSelection);
			}
		});

		text = new Text(parent, SWT.MULTI | SWT.LEAD | SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		text.setEditable(false);
		text.setText("Messages go here");

		// Register as Selection Listener
		getSite().getWorkbenchWindow().getSelectionService()
				.addPostSelectionListener(this);
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

	public void selectionChanged(IWorkbenchPart part, ISelection selection) {

		this.oldPart = part;
		this.oldSelection = selection;

		// Return if we provided this selection
		if (part == this || part == null)
			return;

		// Is this a tree selection?
		if (selection instanceof ITreeSelection) {
			Object firstSegment = ((ITreeSelection) selection).getPaths()[0]
					.getFirstSegment();

			if (firstSegment instanceof Host && firstSegment != currentHost) {

				// Stop the monitor thread
				stopMonitor();

				AS400 theAS400 = null;
				Host host = (Host) firstSegment;
				currentHost = host;
				theAS400 = ((ToolboxConnectorService) host
						.getConnectorServices()[0]).getAS400(true);
				if (theAS400 == null) {
					currentHost = null;
				} else {
					loadMessageQueue(theAS400);
					waitForMessage();
				}
			}
		}
	}

	public void waitForMessage() {

		final MessageQueue queue = this.queue;

		Runnable runner = new Runnable() {

			public void run() {

				String txt = "";
				QueuedMessage msg = null;

				try {
					Thread.sleep(10000);
					queue.load();
					if (queue.getLength() > 0)
						msg = queue.getMessages(0, 1)[0];
					if (msg != null
							&& !msg.getDate().equals(lastMessage.getDate()))
						txt = msg.getText();

					if (txt.length() > 0) {
						loadMessageQueue(queue.getSystem());

						if (alert && msg.getSeverity() >= severityInt) {
							final String text2 = txt;
							Display.getDefault().syncExec(new Runnable() {
								public void run() {
									MessageDialog.openInformation(Display
											.getDefault().getActiveShell(),
											"New Message", text2);
								}
							});
						}
					}

					waitForMessage();

				} catch (Exception e) {
					txt = "Error " + e.getMessage();
					setText(txt);
					return;
				}
			}
		};

		monitorThread = new Thread(runner);
		monitorThread.setName("Message Monitor");
		monitorThread.start();

	}

	private void loadMessageQueue(AS400 theAS400) {

		// Setup the correct message queue
		if (user.equals("QSYSOPR"))
			queue = new MessageQueue(theAS400, "/qsys.lib/qsysopr.msgq");
		else
			queue = new MessageQueue(theAS400);

		queue.setListDirection(false);

		// Get the messages from the queue
		try {
			int queueLength = queue.getLength();
			if (queueLength == 0) {
				setText("No messages in queue");
				return;
			}
			queue.getMessages(0, queueLength > 50 ? 50 : queueLength);
			queue.load();
			QueuedMessage[] result = queue.getMessages(0, queueLength > 50 ? 50
					: queueLength);
			StringBuffer messageText = new StringBuffer();
			lastMessage = null;
			for (QueuedMessage msg : result) {
				messageText.append(msg.getText() + "\n\r");
				if (lastMessage == null)
					lastMessage = msg;
			}

			setText(messageText.toString());

		} catch (Exception e) {
			setText(e.getMessage());
		}
	}

	@Override
	public void dispose() {
		getSite().getWorkbenchWindow().getSelectionService()
				.removePostSelectionListener(this);
		stopMonitor();
		super.dispose();
	}

	private void setText(final String message) {
		getSite().getShell().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				text.setText(message);
			}
		});
	}

	private void stopMonitor() {
		// Stop the monitor thread
		if (monitorThread != null && monitorThread.isAlive()) {
			monitorThread.interrupt();
		}
	}

}