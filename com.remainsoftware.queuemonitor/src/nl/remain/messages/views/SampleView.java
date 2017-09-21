package nl.remain.messages.views;

import java.util.Calendar;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.rse.core.model.Host;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import utilities.ToolboxJarMaker;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.MessageQueue;
import com.ibm.as400.access.QueuedMessage;
import com.ibm.etools.iseries.connectorservice.ToolboxConnectorService;
import com.ibm.etools.iseries.subsystems.qsys.objects.QSYSRemoteFactory;
import com.ibm.etools.iseries.subsystems.qsys.util.QSYSSubSystemUtil;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view
 * shows data obtained from the model. The sample creates a dummy model on the
 * fly, but a real implementation would connect to the model available either in
 * this or another plug-in (e.g. the workspace). The view is connected to the
 * model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be
 * presented in the view. Each view can present the same model objects using
 * different labels and icons, if needed. Alternatively, a single label provider
 * can be shared between views in order to ensure that objects of the same type
 * are presented in the same way everywhere.
 * <p>
 */

public class SampleView extends ViewPart implements ISelectionListener {
	private TableViewer viewer;

	private Action action1;

	private Action action2;

	private Action doubleClickAction;

	public MessageQueue queue;

	private AS400 toolBox;

	private Calendar prev;

	/*
	 * The content provider class is responsible for providing objects to the
	 * view. It can wrap existing objects in adapters or simply return objects
	 * as-is. These objects may be sensitive to the current input of the view,
	 * or ignore it and always show the same content (like Task List, for
	 * example).
	 */

	class ViewContentProvider implements IStructuredContentProvider {
		private Viewer viewer2;

		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
			this.viewer2 = v;
		}

		public void dispose() {
		}

		public Object[] getElements(Object parent) {

			// if (1 == 1)
			// return new String[] { "one", "three" };

			System.out.println("hallo");

			if (parent instanceof String) {
				return new Object[] { parent };
			}

			// toolBox = null;
			AS400 newToolbox = null;
			if (parent instanceof Host) {

				Host host = (Host) parent;

				newToolbox = ((ToolboxConnectorService) host
						.getConnectorServices()[0]).getAS400(true);

			}

			if (newToolbox != null)
				if (newToolbox != toolBox) {
					toolBox = newToolbox;
					queue = new MessageQueue(toolBox);
					queue.setListDirection(false);
					try {
						queue.getMessages(-1, -1);
						queue.load();
						QueuedMessage[] result = queue.getMessages(-1, -1);
						waitForMessage();
						return result;
					} catch (Exception e) {

						return e.getStackTrace();
					}

				}
			String result = "You have selected an object of class "
					+ parent.getClass().getName() + ". (" + parent.toString()
					+ ")";

			return new Object[] { result };
		}
	}

	class ViewLabelProvider extends LabelProvider implements
			ITableLabelProvider {
		public String getColumnText(Object obj, int index) {
			return obj.toString();
		}

		public Image getColumnImage(Object obj, int index) {
			return getImage(obj);
		}

		public Image getImage(Object obj) {
			return PlatformUI.getWorkbench().getSharedImages().getImage(
					ISharedImages.IMG_OBJ_ELEMENT);
		}
	}

	/**
	 * The constructor.
	 */
	public SampleView() {
	}

	public void waitForMessage() {

		final MessageQueue dd = queue;

		Runnable runner = new Runnable() {

			public void run() {

				System.out.println("running");

				String text = "";
				try {
					QueuedMessage msg = dd.receive(null, 10, dd.OLD, dd.ANY);
					if (msg != null)
						text = msg.getText();
				} catch (Exception e) {
					text = "Error " + e.getMessage();
				}

				if (text.length() > 0) {
					final String text2 = text;
					Display.getDefault().syncExec(new Runnable() {

						public void run() {
							MessageDialog.openInformation(Display.getDefault()
									.getActiveShell(), "New Message", text2);

						}
					});
				}
				if (!text.startsWith("Error")) {

					waitForMessage();
				}

			}

		};

		new Thread(runner).start();

	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	public void createPartControl(Composite parent) {
		viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL
				| SWT.V_SCROLL);
		viewer.setContentProvider(new ViewContentProvider());
		viewer.setLabelProvider(new ViewLabelProvider());
		viewer.setInput("Select Something");
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
		getSite().getWorkbenchWindow().getSelectionService()
				.addSelectionListener(this);

	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				SampleView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(action1);
		manager.add(new Separator());
		manager.add(action2);
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(action1);
		manager.add(action2);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(action1);
		// manager.add(action2);
	}

	private void makeActions() {
		action1 = new Action() {
			public void run() {
				toolBox = null;
			}
		};
		action1.setText("Rest");
		action1.setToolTipText("Reset");
		action1.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_TOOL_REDO));

		action2 = new Action() {
			public void run() {
			}
		};
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
	}

	private void showMessage(String message) {
		MessageDialog.openInformation(viewer.getControl().getShell(),
				"Sample View", message);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	public void selectionChanged(IWorkbenchPart sourcepart, ISelection arg1) {

		if (sourcepart == SampleView.this)
			return;

		/*
		 * Flush
		 */
		if (prev == null) {
			prev = Calendar.getInstance();
			return;
		}

		long mils = Calendar.getInstance().getTimeInMillis()
				- prev.getTimeInMillis();
		prev = Calendar.getInstance();
		if (mils < 1000) {
			System.out.println("flushing " + mils);
			toolBox = null;
			return;
		}

		if (arg1 instanceof ITreeSelection)
			viewer.setInput(((ITreeSelection) arg1).getPaths()[0]
					.getFirstSegment());
	}

	@Override
	public void dispose() {
		getSite().getWorkbenchWindow().getSelectionService()
				.removeSelectionListener(this);
		super.dispose();
	}
}