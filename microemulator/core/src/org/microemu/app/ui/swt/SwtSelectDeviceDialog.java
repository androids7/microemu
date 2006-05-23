/*
 *  MicroEmulator
 *  Copyright (C) 2002-2003 Bartek Teodorczyk <barteo@barteo.net>
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.microemu.app.ui.swt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.microemu.app.Config;
import org.microemu.app.util.DeviceEntry;
import org.microemu.app.util.ProgressJarClassLoader;
import org.microemu.device.Device;



public class SwtSelectDeviceDialog extends SwtDialog
{
	private Button btAdd;
	private Button btRemove;
	private Button btDefault;
	private List lsDevices;
	private Vector deviceModel;
	private DeviceEntry selectedEntry;
  
	private Listener btAddListener = new Listener()
	{
		private FileDialog fileDialog = null;
    
		public void handleEvent(Event event)
		{
			if (fileDialog == null) {
				fileDialog = new FileDialog(getShell(), SWT.OPEN);
				fileDialog.setText("Open device profile file...");
				fileDialog.setFilterNames(new String[] {"Device profile (*.dev)"});
				fileDialog.setFilterExtensions(new String[] {"*.dev"});
			}
      
			ProgressJarClassLoader loader = new ProgressJarClassLoader(this.getClass().getClassLoader());
      
			fileDialog.open();
			
			if (fileDialog.getFileName() != null) {
				String deviceClassName = null;
				String deviceName = null;
				try {
					JarFile jar = new JarFile(
							new File(fileDialog.getFilterPath(), fileDialog.getFileName()));
					Manifest manifest = jar.getManifest();
					if (manifest == null) {
						SwtMessageDialog.openError(getShell(),
								"Error", "Missing manifest in dev file.");
						return;
					}          
					Attributes attrs = manifest.getMainAttributes();
          
					deviceName = attrs.getValue("Device-Name");
					if (deviceName == null) {
						SwtMessageDialog.openError(getShell(),
								"Error", "Missing Device-Name entry in jar manifest.");
						return;
					}
          
					deviceClassName = attrs.getValue("Device-Class");
					if (deviceClassName == null) {
						SwtMessageDialog.openError(getShell(),
								"Error", "Missing Device-Class entry in jar manifest.");
						return;
					}
          
					jar.close();
//					deviceClassName = deviceClassName.replace('.', '/');
//					if (deviceClassName.charAt(0) == '/') {
//						deviceClassName = deviceClassName.substring(1);
//					}
					for (Enumeration e = deviceModel.elements(); e.hasMoreElements(); ) {
						DeviceEntry entry = (DeviceEntry) e.nextElement();
						if (deviceClassName.equals(entry.getClassName())) {
							SwtMessageDialog.openInformation(getShell(),
									"Info", "Device is already added.");
							return;
						}
					}
          
					loader.addRepository(new File(fileDialog.getFilterPath(), fileDialog.getFileName()).toURL());
				} catch (IOException ex) {
					SwtMessageDialog.openError(getShell(),
							"Error", "Error reading " + fileDialog.getFileName() + " file.");
					return;
				}
        
				Class deviceClass = null;
				try {
					deviceClass = loader.findClass(deviceClassName);
				} catch (ClassNotFoundException ex) {
					SwtMessageDialog.openError(getShell(),
							"Error", "Cannot find class defined in Device-Class entry in jar manifest.");
					return;
				}
          
				if (!Device.class.isAssignableFrom(deviceClass)) {
					SwtMessageDialog.openError(getShell(),
							"Error", "Cannot find class defined in Device-Class entry in jar manifest.");
					return;
				}
        
				try {
					File deviceFile = File.createTempFile("dev", ".dev", Config.getConfigPath());
					FileInputStream fis  = new FileInputStream(
							new File(fileDialog.getFilterPath(), fileDialog.getFileName()));
					FileOutputStream fos = new FileOutputStream(deviceFile);
					byte[] buf = new byte[1024];
						int i = 0;
						while((i=fis.read(buf))!=-1) {
							fos.write(buf, 0, i);
						}
					fis.close();
					fos.close();
        
					DeviceEntry entry = 
							new DeviceEntry(deviceName, deviceFile.getName(), deviceClassName, false);
					deviceModel.addElement(entry);
					for (i = 0; i < deviceModel.size(); i++) {
						if (deviceModel.elementAt(i) == entry) {
							lsDevices.add(entry.getName());
							lsDevices.select(i);
						}
					}
					lsDevicesListener.widgetSelected(null);
				} catch (IOException ex) {
					System.err.println(ex);
				}
			}
		}
	};
  
	private Listener btRemoveListener = new Listener()
	{
		public void handleEvent(Event event)
		{
			DeviceEntry entry = (DeviceEntry) deviceModel.elementAt(lsDevices.getSelectionIndex());
			File deviceFile = new File(Config.getConfigPath(), entry.getFileName());
			deviceFile.delete();
			if (entry.isDefaultDevice()) {
				for (int i = 0; i < deviceModel.size(); i++) {
					DeviceEntry tmp = (DeviceEntry) deviceModel.elementAt(i);
					if (!tmp.canRemove()) {
						tmp.setDefaultDevice(true);
						lsDevices.setItem(i, tmp.getName() + " (default)");						
						break;
					}
				}
			}
			for (int i = 0; i < deviceModel.size(); i++) {
				if (deviceModel.elementAt(i) == entry) {
					deviceModel.removeElementAt(i);
					lsDevices.remove(i);
					break;
				}
			}
			lsDevicesListener.widgetSelected(null);
		}
	};
  
	private Listener btDefaultListener = new Listener()
	{
		public void handleEvent(Event event)
		{
			DeviceEntry entry = (DeviceEntry) deviceModel.elementAt(lsDevices.getSelectionIndex());
			for (int i = 0; i < deviceModel.size(); i++) {
				DeviceEntry tmp = (DeviceEntry) deviceModel.elementAt(i);
				if (tmp == entry) {
					tmp.setDefaultDevice(true);
					lsDevices.setItem(i, tmp.getName() + " (default)");
				} else {
					tmp.setDefaultDevice(false);
					lsDevices.setItem(i, tmp.getName());
				}
			}
			btDefault.setEnabled(false);
		}
	};
	
	SelectionAdapter lsDevicesListener = new SelectionAdapter()
	{
		public void widgetSelected(SelectionEvent e) 
		{
			int index = lsDevices.getSelectionIndex();
			if (index != -1) {
				selectedEntry = (DeviceEntry) deviceModel.elementAt(index);
				if (selectedEntry.isDefaultDevice()) {
					btDefault.setEnabled(false);
				} else {
					btDefault.setEnabled(true);
				}
				if (selectedEntry.canRemove()) {
					btRemove.setEnabled(true);
				} else {
					btRemove.setEnabled(false);
				}
				btOk.setEnabled(true);
			} else {
				selectedEntry = null;
				btDefault.setEnabled(false);
				btRemove.setEnabled(false);
				btOk.setEnabled(false);
			}
		}
	};


	public SwtSelectDeviceDialog(Shell parent)
	{
		super(parent);

    Vector devs = Config.getDevices();
		for (int i = 0; i < devs.size(); i++) {
			DeviceEntry entry = (DeviceEntry) devs.elementAt(i);
			if (entry.isDefaultDevice()) {
				selectedEntry = entry;
			}
		}
	}
  
  
	protected void configureShell(Shell shell) 
	{
		super.configureShell(shell);

		shell.setText("Select device...");
	}


	protected Control createDialogArea(Composite composite) 
	{
		GridLayout gridLayout = new GridLayout();
   	gridLayout.numColumns = 1;
		composite.setLayout(gridLayout);

		Group gpDevices = new Group(composite, SWT.NONE);
		gpDevices.setText("Installed devices");
		gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
	  gpDevices.setLayout(gridLayout);
		gpDevices.setLayoutData(new GridData(GridData.FILL_BOTH));

		lsDevices = new List(gpDevices, SWT.SINGLE | SWT.V_SCROLL);
		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 3;
		gridData.grabExcessVerticalSpace = true;
		Rectangle trim = lsDevices.computeTrim(0, 0, 0, lsDevices.getItemHeight() * 5);
		gridData.heightHint = trim.height;
		lsDevices.setLayoutData(gridData);
		lsDevices.addSelectionListener(lsDevicesListener);
		
		Composite btDevices = new Composite(gpDevices, SWT.NONE);
		gridLayout = new GridLayout();
		gridLayout.numColumns = 3;
		btDevices.setLayout(gridLayout);
		btDevices.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER));
    
		btAdd = new Button(btDevices, SWT.PUSH);
		btAdd.setText("Add...");
		btAdd.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER));
		btAdd.addListener(SWT.Selection, btAddListener);
		
		btRemove = new Button(btDevices, SWT.PUSH);
		btRemove.setText("Remove");
		btRemove.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER));
		btRemove.addListener(SWT.Selection, btRemoveListener);
		
		btDefault = new Button(btDevices, SWT.PUSH);
		btDefault.setText("Set as default");
		btDefault.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER));
		btDefault.addListener(SWT.Selection, btDefaultListener);
    
    Vector devs = Config.getDevices();
		deviceModel = new Vector();
		for (int i = 0; i < devs.size(); i++) {
			DeviceEntry entry = (DeviceEntry) devs.elementAt(i);
			deviceModel.addElement(entry);
			if (entry.isDefaultDevice()) {
				lsDevices.add(entry.getName() + " (default)");
				lsDevices.select(i);
			} else {
				lsDevices.add(entry.getName());
			}
		}

		return composite;
	}
  
  
	protected Control createButtonBar(Composite parent) 
	{
		Control control = super.createButtonBar(parent);
		
		lsDevicesListener.widgetSelected(null);

		return control;
	}
	

	public DeviceEntry getSelectedDeviceEntry()
	{
		return selectedEntry;
	}
  
  
	public boolean close()
	{
		super.close();
		
		Vector devices = Config.getDevices();
		devices.removeAllElements();
    
		for (Enumeration e = deviceModel.elements(); e.hasMoreElements(); ) {
			devices.add(e.nextElement());
		}
    
		Config.saveConfig("config.xml");
		
		return true;
	}
    
}