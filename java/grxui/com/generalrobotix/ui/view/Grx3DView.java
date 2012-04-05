// -*- indent-tabs-mode: nil; tab-width: 4; -*-
/*
 * Copyright (c) 2008, AIST, the University of Tokyo and General Robotix Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 * General Robotix Inc.
 * National Institute of Advanced Industrial Science and Technology (AIST) 
 */
/*
 *  Grx3DView.java
 *
 *  Copyright (C) 2007 GeneralRobotix, Inc.
 *  All Rights Reserved
 *
 *  @author Yuichiro Kawasumi (General Robotix, Inc.)
 */

package com.generalrobotix.ui.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.*;
import javax.media.j3d.*;
import javax.vecmath.*;

import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContext;
import org.omg.PortableServer.POAPackage.ServantNotActive;
import org.omg.PortableServer.POAPackage.WrongPolicy;

import com.sun.j3d.utils.universe.SimpleUniverse;

import jp.go.aist.hrp.simulator.*;

import com.generalrobotix.ui.*;
import com.generalrobotix.ui.util.*;
import com.generalrobotix.ui.util.AxisAngle4d;
import com.generalrobotix.ui.item.GrxModelItem;
import com.generalrobotix.ui.item.GrxWorldStateItem;
import com.generalrobotix.ui.item.GrxModelItem.LinkInfoLocal;
import com.generalrobotix.ui.item.GrxWorldStateItem.CharacterStateEx;
import com.generalrobotix.ui.item.GrxWorldStateItem.WorldStateEx;
import com.generalrobotix.ui.view.tdview.*;
import com.generalrobotix.ui.view.vsensor.Camera_impl;

@SuppressWarnings("serial")
public class Grx3DView 
	extends GrxBaseView 
	implements ThreeDDrawable 
{
	public static final String TITLE = "3DView";
	public static final GraphicsConfiguration graphicsConfiguration = SimpleUniverse.getPreferredConfiguration();
	
	// items
	private GrxWorldStateItem  currentWorld_ = null;
	private List<GrxModelItem> currentModels_ = new ArrayList<GrxModelItem>();
	private double prevTime_ = -1;
	private double dAngle_ = Math.toRadians(0.1);
	private boolean updateModels_=true;
	
	// for scene graph
	private static VirtualUniverse universe_;
	private javax.media.j3d.Locale locale_;
	private BranchGroup    bgRoot_;
	
	private BranchGroup rulerBg_;
	private BranchGroup collisionBg_;
	private float LineWidth_ = 1.0f;
	private float colprop = 10.0f;
	private float coldiff = 0.1f;
	
	// for view
	private Canvas3D  canvas_;
	private Canvas3D offscreen_;
	private BranchGroup offScreenBg_;
    private View view_;
	private TransformGroup tgView_;
	private Transform3D t3dViewHome_ = new Transform3D();
	private ViewInfo info_;
	private BehaviorManager behaviorManager = new BehaviorManager(manager_);
	private Background backGround_ = new Background(0.0f, 0.0f, 0.0f);
	private double[] default_eye =    new double[]{2.0, 2.0, 0.8};
	private double[] default_lookat = new double[]{0.0, 0.0, 0.8};
	private double[] default_upward = new double[]{0.0, 0.0, 1.0};
    
	// for recording movie
	private RecordingManager recordingMgr_;
	private String lastMovieFileName;
	private boolean isRecording_ = false;
	private int recordingStep_ = 0;
	
	// UI objects
    private ObjectToolBar objectToolBar_ = new ObjectToolBar();
	private ViewToolBar viewToolBar_ = new ViewToolBar();
	private JButton btnHomePos_ = new JButton(new ImageIcon(getClass().getResource("/resources/images/home.png")));
	private JToggleButton btnFloor_ = new JToggleButton(new ImageIcon(getClass().getResource("/resources/images/floor.png")));
	private JToggleButton btnCollision_ = new JToggleButton(new ImageIcon(getClass().getResource("/resources/images/collision.png")));
	private JToggleButton btnCoM_ = new JToggleButton(new ImageIcon(getClass().getResource("/resources/images/com.png")));
	private JToggleButton btnCoMonFloor_ = new JToggleButton(new ImageIcon(getClass().getResource("/resources/images/com_z0.png")));
	private JToggleButton btnRec_ = new JToggleButton(new ImageIcon(getClass().getResource("/resources/images/record.png")));
	private JButton btnPlayer_ = new JButton(new ImageIcon(getClass().getResource("/resources/images/movie_player.png")));
	private JButton btnRestore_ = new JButton(new ImageIcon(getClass().getResource("/resources/images/undo.png")));
	private JFileChooser imageChooser_;
	private JLabel lblMode_ = new JLabel("[VIEW]");
	private JLabel lblTarget_ = new JLabel("");
	private JLabel lblValue_  = new JLabel("");
	
	private Shape3D coll;

	public Grx3DView(String name, GrxPluginManager manager) 
	{
		super(name, manager);
		isScrollable_ = false;
		
		JPanel contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());
		contentPane.setBackground(Color.black);
		contentPane.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		lblMode_.setForeground(Color.white);
		lblMode_.setFont(new Font("Monospaced", Font.BOLD, 12));
		lblMode_.setPreferredSize(new Dimension(300, 20));

		lblTarget_.setForeground(Color.white);
		lblTarget_.setFont(new Font("Monospaced", Font.BOLD, 12));
		lblTarget_.setPreferredSize(new Dimension(500, 20));

		lblValue_.setForeground(Color.white);
		lblValue_.setFont(new Font("Monospaced", Font.BOLD, 12));
		lblValue_.setPreferredSize(new Dimension(300, 20));

		canvas_ = new Canvas3D(graphicsConfiguration);
		canvas_.setDoubleBufferEnable(true);
        canvas_.addKeyListener(new ModelEditKeyAdapter());  
		_setupSceneGraph();

		JPanel mainPane = new JPanel(new BorderLayout());
		mainPane.setBackground(Color.black);
		mainPane.add(lblMode_, BorderLayout.NORTH);
		mainPane.add(canvas_, BorderLayout.CENTER);
		contentPane.add(mainPane, BorderLayout.CENTER);
		
		_setupToolBars();
		contentPane.add(objectToolBar_, BorderLayout.WEST);
		contentPane.add(viewToolBar_, BorderLayout.NORTH);
		
		coll = new Shape3D();
		coll.setPickable(false);
		coll.setCapability(Shape3D.ALLOW_GEOMETRY_WRITE);
		try {
			Appearance app = new Appearance();
			LineAttributes latt = new LineAttributes();
			latt.setLineWidth(LineWidth_);
			app.setLineAttributes(latt);
			coll.setAppearance(app);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		BranchGroup bg = new BranchGroup();
		bg.addChild(coll);
		bgRoot_.addChild(bg);
	}
	
	
	public void disableUpdateModel(){
		updateModels_ = false;
	}
	
	public void enableUpdateModel(){
		updateModels_ = true;
	}
	
	private void _setupSceneGraph() {
		universe_ = new VirtualUniverse();
		locale_ = new javax.media.j3d.Locale(universe_);
		bgRoot_ = new BranchGroup();
		
		bgRoot_.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
		bgRoot_.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
		bgRoot_.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
		
		//locale_.addBranchGraph(_createView());
		bgRoot_.addChild(_createLights());
		bgRoot_.addChild(_createView());
	    bgRoot_.compile();
		locale_.addBranchGraph(bgRoot_);
	}
	
	private BranchGroup _createView() {
		BranchGroup bg = new BranchGroup();
	    ViewPlatform platform = new ViewPlatform();
        info_ = new ViewInfo(ViewInfo.VIEW_MODE_ROOM | ViewInfo.FRONT_VIEW, 3.0 );
        view_ = new View();
		tgView_ = new TransformGroup();
		
	    view_.setPhysicalBody(new PhysicalBody());
	    view_.setPhysicalEnvironment(new PhysicalEnvironment());
		view_.setFrontClipPolicy(View.VIRTUAL_EYE);
		view_.setBackClipPolicy(View.VIRTUAL_EYE);
		view_.setFrontClipDistance(0.05);
		view_.setBackClipDistance(50.0);
		view_.setProjectionPolicy(View.PERSPECTIVE_PROJECTION);
        view_.setFieldOfView(Math.PI/4);
	    view_.addCanvas3D(canvas_);
	    
		tgView_.setCapability(TransformGroup.ALLOW_CHILDREN_EXTEND);
		tgView_.setCapability(TransformGroup.ALLOW_CHILDREN_WRITE);
		tgView_.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		tgView_.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		tgView_.setCapability(TransformGroup.ALLOW_LOCAL_TO_VWORLD_READ);
		
	    view_.attachViewPlatform(platform);
	    tgView_.addChild(platform);
	    bg.addChild(tgView_);	    
	    
		_setViewHomePosition();
		
		return bg;
	}
	
	private BranchGroup _createLights() {
		BranchGroup bg = new BranchGroup();
	    DirectionalLight[] light = new DirectionalLight[4];
	    TransformGroup[] tg = new TransformGroup[4];
	    BoundingSphere bounds = new BoundingSphere(new Point3d(0.0,0.0,0.0), 100.0);
	   
		//DirectionalLight dlight = new DirectionalLight(new Color3f(1.0f, 1.0f, 1.0f), new Vector3f(-0.8f, -1.2f, -1.5f));
		//dlight.setInfluencingBounds(bounds);
		//tgView_.addChild(dlight);

	    light[0] = new DirectionalLight(true,   // lightOn
	            new Color3f(0.7f, 0.7f, 0.7f),  // color
	            new Vector3f(0.0f, 0.0f, -1.0f) // direction
	    );
	    
	    light[1] = new DirectionalLight(true,   // lightOn
	            new Color3f(0.4f, 0.4f, 0.4f),  // color
	            new Vector3f(0.0f, 0.0f, -1.0f) // direction
	    );
	    
	    light[2] = new DirectionalLight(true,   // lightOn
	            new Color3f(0.7f, 0.7f, 0.7f),  // color
	            new Vector3f(0.0f, 0.0f, -1.0f) // direction
	    );
	    
	    light[3] = new DirectionalLight(true,   // lightOn
	            new Color3f(0.4f, 0.4f, 0.4f),  // color
	            new Vector3f(0.0f, 0.0f, -1.0f) // direction
	    );
	    
	    for (int i = 0; i < 4; i ++) {
	        light[i].setInfluencingBounds(bounds);
	        tg[i] = new TransformGroup();
	        bg.addChild(tg[i]);
	        tg[i].addChild(light[i]);
	    }

        Transform3D transform = new Transform3D();
        Vector3d pos = new Vector3d();
        AxisAngle4d rot = new AxisAngle4d();
        
        pos.set(10.0, 10.0, 5.0);
        transform.set(pos);
        rot.set(-0.5, 0.5, 0.0, 1.2);
        transform.set(rot);
        tg[0].setTransform(transform);
        
        pos.set(10.0, -10.0, -5.0);
        transform.set(pos);
        rot.set(0.5, 0.5, 0.0, 3.14 - 1.2);
        transform.set(rot);
        tg[1].setTransform(transform);
        
        pos.set(-10.0, -10.0, 5.0);
        transform.set(pos);
        rot.set(0.5, -0.5, 0.0, 1.2);
        transform.set(rot);
        tg[2].setTransform(transform);
        
        pos.set(-10.0, 10.0, -5.0);
        transform.set(pos);
        rot.set(-0.5, -0.5, 0.0, 3.14 - 1.2);
        transform.set(rot);
        tg[3].setTransform(transform);

		// Ambient Light for Alert
		//AmbientLight alight = new AmbientLight(new Color3f(1.0f, 1.0f, 1.0f));
		//alight.setInfluencingBounds(bounds);
		//tg[0].addChild(alight);

		// background
		backGround_.setCapability(Background.ALLOW_COLOR_READ);
		backGround_.setCapability(Background.ALLOW_COLOR_WRITE);
		backGround_.setApplicationBounds(bounds);
		bg.addChild(backGround_);
		
		return bg;
	}

	private void _setupToolBars() {
		btnHomePos_.setToolTipText("go home of eye pos.");
		btnHomePos_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				tgView_.setTransform(t3dViewHome_);
			}
		});

		btnFloor_.setToolTipText("show z=0 plane");
		btnFloor_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (btnFloor_.isSelected()) {
					btnFloor_.setToolTipText("hide z=0 plane");
					if (bgRoot_.indexOfChild(getRuler()) == -1) 
						bgRoot_.addChild(getRuler());
				} else {
					btnFloor_.setToolTipText("show z=0 plane");
					if (bgRoot_.indexOfChild(getRuler()) != -1)
						getRuler().detach();
				}
			}
		});
		btnFloor_.doClick();
		
		btnCollision_.setToolTipText("show Collision");
		btnCollision_.setSelected(true);
		btnCollision_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (btnCollision_.isSelected())
					btnCollision_.setToolTipText("hide Collision");
				else
					btnCollision_.setToolTipText("show Collision");
			}
		});
		
		btnCoM_.setToolTipText("show Center of Mass");
		btnCoM_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean b = btnCoM_.isSelected();
				for (int i=0; i<currentModels_.size(); i++)
					currentModels_.get(i).setVisibleCoM(b);
			};
		});
		
		btnCoMonFloor_.setToolTipText("show Center of Mass on Floor");
		btnCoMonFloor_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean b = btnCoMonFloor_.isSelected();
				for (int i=0; i<currentModels_.size(); i++)
					currentModels_.get(i).setVisibleCoMonFloor(b);
			};
		});
		
		btnRec_.setToolTipText("record");
		btnRec_.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {    
				if (btnRec_.isSelected()) {
					if (currentWorld_ != null && currentWorld_.getLogSize() > 0)
						rec();
					else 
						btnRec_.setSelected(false);
				} else  {
					btnRec_.setSelected(false);
				}
			}
		});
        
		btnPlayer_.setToolTipText("movie player");
		btnPlayer_.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {    
				new SimpleMoviePlayer(lastMovieFileName);
			}
		});
		

		final JButton btnCamera = new JButton("C");
		btnCamera.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				for (int i=0; i<currentModels_.size(); i++) {
					List<Camera_impl> l = currentModels_.get(i).getCameraSequence();
					for (int j=0; j<l.size(); j++) {
						Camera_impl c = l.get(j);
						c.setVisible(!c.isVisible());
					}
				}
			}
		});
		
		viewToolBar_.add(btnHomePos_,0);
		viewToolBar_.add(btnFloor_, 8);
		viewToolBar_.add(btnCollision_, 9);
		viewToolBar_.add(btnCoM_, 10);
		viewToolBar_.add(btnCoMonFloor_, 11);
		viewToolBar_.add(btnRec_);
		viewToolBar_.add(btnPlayer_);
		viewToolBar_.add(btnCamera);

		btnRestore_.setToolTipText("restore model properties");
		btnRestore_.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for (int i=0; i<currentModels_.size(); i++) {
					GrxModelItem item = currentModels_.get(i);
					item.restoreProperties();
				}
			}
		});
		
		objectToolBar_.add(btnRestore_, 0);
		objectToolBar_.setOrientation(JToolBar.VERTICAL);
		
		JToolBar bars[] = new JToolBar[]{viewToolBar_, objectToolBar_};
		for (int i=0; i<bars.length; i++) {
			JToolBar bar = bars[i];
			bar.setFloatable(false);
			for (int j=0; j<bar.getComponentCount(); j++) {
				Component c = bar.getComponent(j);
				if (c instanceof AbstractButton) {	
					AbstractButton b = (AbstractButton)c;
					b.setPreferredSize(GrxBaseView.getDefaultButtonSize());
					b.setMaximumSize(GrxBaseView.getDefaultButtonSize());
				}
			}	
		}
		
        _registerAction();
	}
	
	public Canvas3D getCanvas3D() {
		return canvas_;
	}
	
	public BranchGroup getBranchGroupRoot() {
		return bgRoot_;
	}
	
	public void restoreProperties() {
		super.restoreProperties();
		default_eye    = getDblAry("view.eye",    default_eye);
		default_lookat = getDblAry("view.lookat", default_lookat);
		default_upward = getDblAry("view.upward", default_upward);
		
		btnFloor_.setSelected(isTrue("showScale", true));
		btnCollision_.setSelected(isTrue("showCollision", true));
		btnCoM_.setSelected(isTrue("showCoM", false));
		btnCoMonFloor_.setSelected(isTrue("showCoMonFloor",false));
		
		_setViewHomePosition();
	}
	
	private void _setViewHomePosition() {
		t3dViewHome_.lookAt(
			new Point3d(default_eye), 
			new Point3d(default_lookat), 
			new Vector3d(default_upward));
		t3dViewHome_.invert();
		tgView_.setTransform(t3dViewHome_);
	}
	
	public void itemSelectionChanged(List<GrxBaseItem> itemList) {
		boolean selectionChanged = false;
		for (int i = currentModels_.size()-1 ; i>-1; i--) {
			GrxModelItem item = currentModels_.get(i);
			if (item.isSelected())
				continue;
			currentModels_.remove(item);		
			selectionChanged = true;
		}

		currentWorld_ = null;
		Iterator it = itemList.iterator();
		while (it.hasNext()) {
			GrxBaseItem item = (GrxBaseItem) it.next();
			if (item instanceof GrxModelItem) {
				GrxModelItem modelItem = (GrxModelItem) item;
				if (currentModels_.contains(modelItem))
					continue;
				bgRoot_.addChild(modelItem.bgRoot_);
				currentModels_.add(modelItem);
				selectionChanged = true;
			} else if (item instanceof GrxWorldStateItem) {
				currentWorld_ = (GrxWorldStateItem) item;
			}
		}
		if (selectionChanged && isRunning()) 
			behaviorManager.replaceWorld(itemList);
		
		prevTime_ = -1;
		if (collisionBg_ != null) {
			collisionBg_.detach();
			collisionBg_ = null;
		}	
	}
	
	public boolean setup(List<GrxBaseItem> itemList) {
        behaviorManager.setThreeDViewer(this);
        behaviorManager.setViewIndicator(viewToolBar_);
        behaviorManager.setOperationMode(BehaviorManager.OPERATION_MODE_NONE);
        behaviorManager.setViewMode(BehaviorManager.ROOM_VIEW_MODE);
        behaviorManager.setViewHandlerMode("button_mode_rotation");
		behaviorManager.replaceWorld(itemList);
        viewToolBar_.setMode(ViewToolBar.ROOM_MODE);
        viewToolBar_.setOperation(ViewToolBar.ROTATE);

       	return registerCORBA();
	}
	
	public void control(List<GrxBaseItem> items) {
		if(isRecording_) return;
		
		if (currentModels_.size() == 0)
			return;

		if (behaviorManager.getOperationMode() != BehaviorManager.OPERATION_MODE_NONE && btnCollision_.isSelected()) {
 			if (updateModels_) updateViewSimulator(0);
			_showCollision(behaviorManager.getCollision(currentModels_));
			return;
		}

 		if (currentWorld_ == null){
 			if (updateModels_) updateViewSimulator(0);
			return;
 		}

		WorldStateEx state = currentWorld_.getValue();
		if (state == null){
 			if (updateModels_) updateViewSimulator(0);
			return;
		}
			
		if (state.time == prevTime_)
			return;
		_showCollision(state.collisions);

		if (updateModels_){
			updateModels(state);
			updateViewSimulator(state.time);
		}
		
		prevTime_ = state.time;
	}

	public void updateModels(WorldStateEx state){
		// update models with new WorldState
		for (int i=0; i<currentModels_.size(); i++) {
			GrxModelItem model = currentModels_.get(i);
			CharacterStateEx charStat = state.get(model.getName());
			if (charStat != null) {
				if (charStat.sensorState != null)
					model.setCharacterPos(charStat.position, charStat.sensorState.q);
				else
					model.setCharacterPos(charStat.position, null);
			}
		}
	}
    private void rec(){
		if (isRecording_) 
			return;
		
    	RecordingDialog dialog = new RecordingDialog(manager_.getFrame());
    	if (dialog.showModalDialog() != ModalDialog.OK_BUTTON) {
			btnRec_.setSelected(false);
    	    return;
		}

    	StringBuffer strBuffer = new StringBuffer( dialog.getFileName() );
    	Dimension canvasSize = dialog.getImageSize();
    	int framerate=10;
    	try{
    		framerate = dialog.getFrameRate();
    	}catch(Exception NumberFormatException ){
    		new ErrorDialog(manager_.getFrame(), "Error", "framerate must be integer" ).showModalDialog();
    		btnRec_.setSelected(false);
    		return;
    	}
    	double playbackRate = dialog.getPlaybackRate();
    	
    	if ( chkFileName( strBuffer ) == false ){
            btnRec_.setSelected(false);
            return;
    	}
    	String fileName = strBuffer.substring(0);
 
        GrxDebugUtil.println("ScreenSize: " + canvasSize.width + "x" + canvasSize.height + " (may be)");
        BufferedImage image=new BufferedImage(canvasSize.width,canvasSize.height,BufferedImage.TYPE_INT_ARGB);

        ImageComponent2D buffer=new ImageComponent2D(ImageComponent.FORMAT_RGBA,image,true,false);
		buffer.setCapability(ImageComponent2D.ALLOW_IMAGE_READ);
		
		offscreen_=new Canvas3D(graphicsConfiguration,true);
		offscreen_.setOffScreenBuffer(buffer);
		
		View view=new View();
		view.setPhysicalBody(view_.getPhysicalBody());
		view.setPhysicalEnvironment(view_.getPhysicalEnvironment());
		view.addCanvas3D(offscreen_);
		
		Screen3D screen = canvas_.getScreen3D();
        offscreen_.getScreen3D().setSize(screen.getSize());
		offscreen_.getScreen3D().setPhysicalScreenWidth(screen.getPhysicalScreenWidth());
		offscreen_.getScreen3D().setPhysicalScreenHeight(screen.getPhysicalScreenHeight());
		
		ViewPlatform platform=new ViewPlatform();
		view.attachViewPlatform(platform);
		offScreenBg_ = new BranchGroup();
		offScreenBg_.setCapability(BranchGroup.ALLOW_DETACH);
		offScreenBg_.addChild(platform);
		tgView_.addChild(offScreenBg_);
		
		recordingMgr_ = RecordingManager.getInstance();
    	recordingMgr_.setImageSize(canvasSize.width , canvasSize.height);
    	recordingMgr_.setFrameRate((float)framerate);
    	
    	lastMovieFileName = pathToURL(fileName);
    	ComboBoxDialog cmb = new ComboBoxDialog(
    			manager_.getFrame(), 
    			"Video format", 
    			"Select Video format, Please.",
    			recordingMgr_.preRecord(lastMovieFileName, ImageToMovie.QUICKTIME));
    	String format__ = (String)cmb.showComboBoxDialog();
    	if (format__ == null) {
			btnRec_.setSelected(false);
    		return;
		}
    	
    	try {
    		recordingMgr_.startRecord(format__);
    	} catch (Exception e) {
    		GrxDebugUtil.printErr("Grx3DView.rec():",e);
			JOptionPane.showMessageDialog(manager_.getFrame(), "Failed to Record Movie");
			btnRec_.setSelected(false);
			return;
    	}
			 	
    	recordingStep_ = (int)(currentWorld_.getDbl("logTimeStep", -1.0) * 1000);	
    	recordingStep_ = Math.max((int)(1000.0/framerate*playbackRate), recordingStep_);
		
		
		Thread recThread_ = new Thread() {
			public void run() {
				try {
					int startPosition =0;
					int endPosition = currentWorld_.getLogSize();  
					double playRateLogTime_ = (double)recordingStep_/1000.0;
					double prevTime = -recordingStep_;
					for (int p=startPosition; p < endPosition; p++) {
						if(!btnRec_.isSelected())break;
						double time = currentWorld_.getTime(p);
						if (time - prevTime >= playRateLogTime_) {
							prevTime = time;
							currentWorld_.setPosition(p);
							WorldStateEx state = currentWorld_.getValue();
							_showCollision(state.collisions);
							updateModels(state);
							_doRecording();
						}
					}
					stopRecording();
				} catch (Exception e) {
					JOptionPane.showMessageDialog(manager_.getFrame(),
						"Recording Interrupted by Exception.", 
						"Recording Interupted",
						JOptionPane.ERROR_MESSAGE, manager_.ROBOT_ICON);
					stopRecording();
					GrxDebugUtil.printErr("Recording Interrupted by Exception:",e);
				}
			}
		};
		
		isRecording_ = true;
		recThread_.start();
    }
    
    private void stopRecording(){
    	recordingMgr_.endRecord();
		isRecording_ = false;
		btnRec_.setSelected(false);
		tgView_.removeChild(offScreenBg_);
		JOptionPane.showMessageDialog(manager_.getFrame(),"Recording finished" );
    }
    
    private boolean fileOverwriteDialog(String fileName){
		File file = new File(fileName);
		if (!file.exists()) {
			return true;
		}

		if (file.isFile()) {
    		int ans = JOptionPane.showConfirmDialog(manager_.getFrame(),
    			fileName + " " + "is already exist. Are you over write?",
    	    	"File is already exist.",JOptionPane.YES_NO_OPTION);
    		if (ans == JOptionPane.YES_OPTION) {
    	    	return true;
			}
		}
    	
    	return false;
    }
    
    private String pathToURL(String path) {
    	if (path.startsWith("file:///")) {
		    	    //String filePath = path.substring(8);
    	    path = path.replace(java.io.File.separatorChar, '/');
    	    return path;
    	}
    	if (path.startsWith("http://")) {
    	    return path;
    	}
    	if (!path.startsWith(java.io.File.separator) && (path.indexOf(':') != 1)) {
    	    path = System.getProperty("user.dir") + java.io.File.separator + path;
    	}
    	if (path.indexOf(':') == 1) {
    	    path = path.replace(java.io.File.separatorChar, '/');
    	    return "file:///" + path;
    	}
    	return "file://" + path;
    }
        
	private void _doRecording() {
		offscreen_.renderOffScreenBuffer();
		offscreen_.waitForOffScreenRendering();
		recordingMgr_.pushImage( offscreen_.getOffScreenBuffer().getImage() );
	}
	
	private void _showCollision(Collision[] collisions) {
		/*if (collisionBg_ != null)
			collisionBg_.detach();
		collisionBg_ = null;*/
		
		coll.removeAllGeometries();
		if (collisions == null || collisions.length <= 0 || !btnCollision_.isSelected()) 
			return;
			
		int length = 0;
		for (int i = 0; i < collisions.length; i++) {
			if (collisions[i].points != null)
				length += collisions[i].points.length;
		}
		if (length > 0) {
			CollisionPoint[] cd = new CollisionPoint[length];
			for (int i=0, n=0; i<collisions.length; i++) {
				for (int j=0; j<collisions[i].points.length; j++)
					cd[n++] = collisions[i].points[j];
			}
			
			Point3d[] p3d = new Point3d[cd.length * 2];
			for (int j=0; j<cd.length; j++) {
				p3d[j*2] = new Point3d(cd[j].position);
				
				Vector3d pole = new Vector3d(cd[j].normal);
				pole.normalize();
				float depth = (float) cd[j].idepth*colprop+coldiff;
				p3d[j*2+1] = new Point3d(
					cd[j].position[0] + pole.x*depth,
					cd[j].position[1] + pole.y*depth,
					cd[j].position[2] + pole.z*depth
				);
			}

			LineArray la = new LineArray(p3d.length, LineArray.COLOR_3
					| LineArray.COORDINATES | LineArray.NORMALS);
			la.setCoordinates(0, p3d);
			
			Vector3f[] v3f = new Vector3f[p3d.length];
			Color3f[]  c3f =  new Color3f[p3d.length];
			for (int i=0; i<v3f.length; i++) {
				v3f[i] = new Vector3f(0.0f, 0.0f, 1.0f);
				if ((i % 2) == 0) 
					c3f[i] = new Color3f(0.0f, 0.8f, 0.8f);
				else
					c3f[i] = new Color3f(0.8f, 0.0f, 0.8f);
			}
			la.setNormals(0, v3f);
			la.setColors(0, c3f);
			coll.addGeometry(la);
			/*Shape3D s3d = new Shape3D();
			s3d.setPickable(false);
			s3d.addGeometry(la);
			try {
				Appearance app = new Appearance();
				LineAttributes latt = new LineAttributes();
				latt.setLineWidth(LineWidth_);
				app.setLineAttributes(latt);
				s3d.setAppearance(app);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			TransformGroup tg = new TransformGroup();
			tg.addChild(s3d);
			collisionBg_ = new BranchGroup();
			collisionBg_.setCapability(BranchGroup.ALLOW_DETACH);
			collisionBg_.addChild(tg);
			bgRoot_.addChild(collisionBg_);*/
		} else {
			coll.addGeometry(null);
		}
	}

	public void updateViewSimulator(double time) {
		for (int i=0; i<currentModels_.size(); i++) {
			List<Camera_impl> l = currentModels_.get(i).getCameraSequence();
			for (int j=0; j<l.size(); j++) {
				Camera_impl c = l.get(j);
				if (c.isVisible()) c.updateView(time);
			}
		}
	}
	
	public void showViewSimulator() {
		for (int i=0; i<currentModels_.size(); i++) {
			List<Camera_impl> l = currentModels_.get(i).getCameraSequence();
			for (int j=0; j<l.size(); j++) {
				Camera_impl c = l.get(j);
				c.setVisible(true);
			}
		}
	}

	public BranchGroup getRuler() {
		if (rulerBg_ == null) {
			rulerBg_ = new BranchGroup();
			rulerBg_.setCapability(BranchGroup.ALLOW_DETACH);
			int n = 40; // number of lines
			Point3d[] p = new Point3d[n * 4];
			double width = n/2.0;
			for (int i=0; i<n; i++) {
				p[2*i]       = new Point3d(-width+i, -width, 0.0);
				p[2*i+1]     = new Point3d(-width+i,  width, 0.0);
				p[2*i+n*2]   = new Point3d(-width, -width+i, 0.0);
				p[2*i+n*2+1] = new Point3d( width, -width+i, 0.0);
			}
			LineArray geometry = new LineArray(p.length,
					GeometryArray.COORDINATES | GeometryArray.COLOR_3);
			geometry.setCoordinates(0, p);
			for (int i = 0; i < p.length; i++)
				geometry.setColor(i, new Color3f(Color.white));

			Shape3D shape = new Shape3D(geometry);
			shape.setPickable(false);
			rulerBg_.addChild(shape);
			rulerBg_.compile();
		}
		return rulerBg_;
	}
	
	public boolean registerCORBA() {
		NamingContext rootnc = GrxCorbaUtil.getNamingContext();
		
		OnlineViewer_impl olvImpl = new OnlineViewer_impl();
		OnlineViewer olv = olvImpl._this(manager_.orb_);//GrxCorbaUtil.getORB());
		NameComponent[] path1 = {new NameComponent("OnlineViewer", "")};
		
		ViewSimulator_impl  viewImpl = new ViewSimulator_impl();
		ViewSimulator view = viewImpl._this(manager_.orb_);//GrxCorbaUtil.getORB());
		NameComponent[] path2 = {new NameComponent("ViewSimulator", "")};
		
		try {
			rootnc.rebind(path1, olv);
			rootnc.rebind(path2, view);
		} catch (Exception ex) {
			GrxDebugUtil.println("3DVIEW : failed to bind to localhost NameService");
			return false;
		}
 		
		GrxDebugUtil.println("3DVIEW : successfully bound to localhost NameService");
		return true;
	}
	
	private class ModelEditKeyAdapter extends KeyAdapter {
        public void keyPressed(KeyEvent arg0) {
        	GrxModelItem item = null;
        	LinkInfoLocal li = null;
       	  	for (int i=0; i<currentModels_.size(); i++) {
        	  	item = (GrxModelItem)currentModels_.get(i);
        	  	li = item.activeLinkInfo_;
        	  	if (li != null)
        		  	break;
       	  	}
       	  	if (li == null) {
       	  		arg0.consume();
       	  		return;
       	  	}
           	  
          	KeyStroke ks = KeyStroke.getKeyStrokeForEvent(arg0);
          	if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_UP,0) ||
          			ks == KeyStroke.getKeyStroke(KeyEvent.VK_K,0)) {
          		int next = li.jointId-1;
          		if (next >= 0) {
          			for (int j=0; j<item.lInfo_.length; j++) {
          				if (next == item.lInfo_[j].jointId) {
          					item.activeLinkInfo_ = item.lInfo_[j];
          					behaviorManager.setPickTarget(item.lInfo_[j].tg);
          					break;
          				}
          			}
          		}
          	} else if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,0) ||
          			ks == KeyStroke.getKeyStroke(KeyEvent.VK_J,0)) {
          		int next = li.jointId+1;
  				for (int j=0; j<item.lInfo_.length; j++) {
      				if (next == item.lInfo_[j].jointId) {
      					item.activeLinkInfo_ = item.lInfo_[j];
          				behaviorManager.setPickTarget(item.lInfo_[j].tg);
      					break;
      				}
       			}
          	} else if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,KeyEvent.SHIFT_MASK) ||
          			ks == KeyStroke.getKeyStroke(KeyEvent.VK_H,KeyEvent.SHIFT_MASK)) {
          		li.jointValue -= dAngle_;
          		
          		if (li.llimit[0] < li.ulimit[0])
          			li.jointValue = Math.max(li.jointValue, li.llimit[0]);
          		item.calcForwardKinematics();
        	 	item.setProperty(li.name+".angle",String.valueOf(li.jointValue));
        	 	
          	} else if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,KeyEvent.SHIFT_MASK) ||
          			ks == KeyStroke.getKeyStroke(KeyEvent.VK_L,KeyEvent.SHIFT_MASK)) {
          		li.jointValue += dAngle_;
          		if (li.llimit[0] < li.ulimit[0])
          			li.jointValue = Math.min(li.jointValue, li.ulimit[0]);
        	 	item.calcForwardKinematics();
        	 	item.setProperty(li.name+".angle",String.valueOf(li.jointValue));
        	 	
          	} else if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_H,0) ||
          			ks == KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,0)) {
          		li.jointValue -= dAngle_*20;
          		if (li.llimit[0] < li.ulimit[0])
          			li.jointValue = Math.max(li.jointValue, li.llimit[0]);
        	 	item.calcForwardKinematics();
        	 	item.setProperty(li.name+".angle",String.valueOf(li.jointValue));
        	 	
          	} else if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_L,0) ||
          			ks == KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,0)) {
          		li.jointValue += dAngle_*20;
          		if (li.llimit[0] < li.ulimit[0])
          			li.jointValue = Math.min(li.jointValue, li.ulimit[0]);
        	 	item.calcForwardKinematics();
        	 	item.setProperty(li.name+".angle",String.valueOf(li.jointValue));
          	}
          	arg0.consume();
        }     
	}
	
	private class ViewSimulator_impl extends ViewSimulatorPOA {
		public void destroy() {
		}

		public void getCameraSequence(CameraSequenceHolder arg0) {
			List<Camera_impl> allList = new ArrayList<Camera_impl>();
			for (int i=0; i<currentModels_.size(); i++) {
				List<Camera_impl> l = currentModels_.get(i).getCameraSequence();
				allList.addAll(l);
			}

			arg0.value = new Camera[allList.size()];
			for (int i=0; i<allList.size(); i++) {
				try {
					arg0.value[i] = CameraHelper.narrow(manager_.poa_.servant_to_reference(allList.get(i)));
				} catch (ServantNotActive e) {
					e.printStackTrace();
				} catch (WrongPolicy e) {
					e.printStackTrace();
				}
			}
		}
		
		public void getCameraSequenceOf(String objectName, CameraSequenceHolder arg1) {
			for (int i=0; i<currentModels_.size(); i++) {
				GrxModelItem item = currentModels_.get(i);
				if (item.getName().equals(objectName)) {
					List<Camera_impl> l = item.getCameraSequence();
					arg1.value = new Camera[l.size()];
					for (int j=0; j<l.size(); j++) {
						try {
							arg1.value[j] = CameraHelper.narrow(manager_.poa_.servant_to_reference(l.get(j)));
						} catch (ServantNotActive e) {
							e.printStackTrace();
						} catch (WrongPolicy e) {
							e.printStackTrace();
						}
					}
					return;
				}
			}
			arg1.value = new Camera[0];
		}

		public void loadObject(String arg0, String arg1) {}
		public void updateScene(WorldState arg0) { }
        public void registerCharacter(String name, BodyInfo binfo) {}

	}
	
	private class OnlineViewer_impl extends OnlineViewerPOA {
		private double prevTime = 0.0;
		private boolean firstTime_ = true;
		
		public void clearLog() {
			if (currentWorld_ != null)
				currentWorld_.clearLog();
			firstTime_ = true;
		}

		public void load(String name, String url) {
			System.out.println(name+":"+url);
			try {
				URL u = new URL(url);
				url = u.getFile();
				manager_.loadItem(GrxModelItem.class, name, url);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		public boolean getPosture(String name , DblSequenceHolder posture) {
			Object obj = manager_.getItem(GrxModelItem.class, name);
			if (obj != null) {
				GrxModelItem model = (GrxModelItem)obj;
				posture.value =	model.getJointValues();
				return true;
			}
			posture.value = new double[0];
			return false;
		}
		public void update(WorldState arg0) {
			GrxWorldStateItem.WorldStateEx statex = new GrxWorldStateItem.WorldStateEx(arg0);
			if (currentWorld_ == null) {
				currentWorld_ = (GrxWorldStateItem)manager_.createItem(GrxWorldStateItem.class, null);
			}
			if (firstTime_) {
				currentWorld_.setDbl("totalTime", 60.0);
				String[] chars = statex.characters();
				for (int i=0; i<chars.length; i++) {
					GrxModelItem model = (GrxModelItem)manager_.getItem(GrxModelItem.class, chars[i]);
					currentWorld_.registerCharacter(chars[i], model.cInfo_);
				}
			}
			double t = statex.time;
			currentWorld_.addValue(t, statex);
			if (t > 0 && prevTime > 0)
			  currentWorld_.setDbl("logTimeStep", t - prevTime);
			prevTime = t;
            if (firstTime_) {
                firstTime_ = false;
                currentWorld_.setDbl("totalTime", GrxWorldStateItem.DEFAULT_TOTAL_TIME);
            }
		}
		
		public void clearData() {}
		public void drawScene(WorldState arg0) {
		  update(arg0);
		  prevTime_ = -1;
		}
		public void setLineScale(float arg0) {}
		public void setLineWidth(float arg0) {}

        public void setLogName(String name) {}
	}
	
	public void attach(BranchGroup bg) {
		bgRoot_.addChild(bg);
	}

	public String getFullName() {
		return getName();
	}

	public TransformGroup getTransformGroupRoot() {
		return tgView_;
	}

	public ViewInfo getViewInfo() {
		return info_;
	}

	public void setDirection(int dir) {
	}

	public void setTransform(Transform3D transform) {
		tgView_.setTransform(transform);
	}

	public void setViewMode(int mode) {
	}
	
	private void _registerAction() {
	    GUIAction.ROOM_VIEW.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				info_.setViewMode(ViewInfo.VIEW_MODE_ROOM);
				view_.setProjectionPolicy(View.PERSPECTIVE_PROJECTION);
				behaviorManager.setViewMode(BehaviorManager.ROOM_VIEW_MODE);
				viewToolBar_.setMode(ViewToolBar.ROOM_MODE);
			}
		});

		GUIAction.WALK_VIEW.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				info_.setViewMode(ViewInfo.VIEW_MODE_WALK);
				view_.setProjectionPolicy(View.PERSPECTIVE_PROJECTION);
				behaviorManager.setViewMode(BehaviorManager.WALK_VIEW_MODE);
				viewToolBar_.setMode(ViewToolBar.WALK_MODE);
			}
		});

		GUIAction.FRONT_VIEW.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (info_.getDirection() != ViewInfo.FRONT_VIEW)
					info_.setDirection(ViewInfo.FRONT_VIEW);
				info_.setViewMode(ViewInfo.VIEW_MODE_PARALLEL);
				view_.setProjectionPolicy(View.PARALLEL_PROJECTION);
				setTransform(info_.getTransform());
				behaviorManager.setViewMode(BehaviorManager.PARALLEL_VIEW_MODE);
				viewToolBar_.setMode(ViewToolBar.PARALLEL_MODE);
			}
		});

		GUIAction.BACK_VIEW.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (info_.getDirection() != ViewInfo.BACK_VIEW)
					info_.setDirection(ViewInfo.BACK_VIEW);
				info_.setViewMode(ViewInfo.VIEW_MODE_PARALLEL);
				view_.setProjectionPolicy(View.PARALLEL_PROJECTION);
				setTransform(info_.getTransform());
				behaviorManager.setViewMode(BehaviorManager.PARALLEL_VIEW_MODE);
				viewToolBar_.setMode(ViewToolBar.PARALLEL_MODE);
			}
		});

		GUIAction.LEFT_VIEW.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (info_.getDirection() != ViewInfo.LEFT_VIEW)
					info_.setDirection(ViewInfo.LEFT_VIEW);
				info_.setViewMode(ViewInfo.VIEW_MODE_PARALLEL);
				view_.setProjectionPolicy(View.PARALLEL_PROJECTION);
				setTransform(info_.getTransform());
				behaviorManager.setViewMode(BehaviorManager.PARALLEL_VIEW_MODE);
				viewToolBar_.setMode(ViewToolBar.PARALLEL_MODE);
			}
		});

		GUIAction.RIGHT_VIEW.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (info_.getDirection() != ViewInfo.RIGHT_VIEW)
					info_.setDirection(ViewInfo.RIGHT_VIEW);
				info_.setViewMode(ViewInfo.VIEW_MODE_PARALLEL);
				view_.setProjectionPolicy(View.PARALLEL_PROJECTION);
				setTransform(info_.getTransform());
				behaviorManager.setViewMode(BehaviorManager.PARALLEL_VIEW_MODE);
				viewToolBar_.setMode(ViewToolBar.PARALLEL_MODE);
			}
		});

		GUIAction.TOP_VIEW.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (info_.getDirection() != ViewInfo.TOP_VIEW) 
					info_.setDirection(ViewInfo.TOP_VIEW);
				info_.setViewMode(ViewInfo.VIEW_MODE_PARALLEL);
				view_.setProjectionPolicy(View.PARALLEL_PROJECTION);
				setTransform(info_.getTransform());
				behaviorManager.setViewMode(BehaviorManager.PARALLEL_VIEW_MODE);
				viewToolBar_.setMode(ViewToolBar.PARALLEL_MODE);
			}
		});

		GUIAction.BOTTOM_VIEW.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (info_.getDirection() != ViewInfo.BOTTOM_VIEW)
					info_.setDirection(ViewInfo.BOTTOM_VIEW);
				info_.setViewMode(ViewInfo.VIEW_MODE_PARALLEL);
				view_.setProjectionPolicy(View.PARALLEL_PROJECTION);
				setTransform(info_.getTransform());
				behaviorManager.setViewMode(BehaviorManager.PARALLEL_VIEW_MODE);
				viewToolBar_.setMode(ViewToolBar.PARALLEL_MODE);
			}
		});
        GUIAction.VIEW_ZOOM_MODE.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				behaviorManager.setViewHandlerMode("button_mode_zoom");
				// viewHandlerMode_[currentViewer_] = "button_mode_zoom";
				viewToolBar_.setOperation(ViewToolBar.ZOOM);
				//objectToolBar_.selectNone();
				//lblMode_.setText("[ VIEW ]");
			}
		});

		GUIAction.VIEW_ROTATION_MODE.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				behaviorManager.setViewHandlerMode("button_mode_rotation");
				// viewHandlerMode_[currentViewer_] = "button_mode_rotation";
				viewToolBar_.setOperation(ViewToolBar.ROTATE);
				//objectToolBar_.selectNone();
				//lblMode_.setText("[ VIEW ]");
			}
		});
		
        GUIAction.VIEW_PAN_MODE.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                behaviorManager.setViewHandlerMode("button_mode_translation");
                // viewHandlerMode_[currentViewer_] = "button_mode_translation";
                viewToolBar_.setOperation(ViewToolBar.PAN);
                //objectToolBar_.selectNone();
				//lblMode_.setText("[ VIEW ]");
            }
        });
        
		GUIAction.WIRE_FRAME.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Iterator it = manager_.getItemMap(GrxModelItem.class).values().iterator();
				while (it.hasNext()) {
					((GrxModelItem)it.next()).setWireFrame(viewToolBar_.isWireFrameSelected());
				}
			}
		});

		GUIAction.BG_COLOR.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// Bring up a color chooser

				Color3f oldColor = new Color3f();
				backGround_.getColor(oldColor);
				Color c = JColorChooser.showDialog(manager_.getFrame(),
						MessageBundle.get("dialog.bgcolor"), new Color(
								oldColor.x, oldColor.y, oldColor.z));
				if (c != null) {
					backGround_.setColor(new Color3f(c));
				}
			}
		});

		GUIAction.CAPTURE.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//OnScreen way

				Raster raster = null;

				// Color info for reading : type int, Alpha:8bit, R:8bit, G:8bit, B:8bit
				BufferedImage bimageRead = new BufferedImage(canvas_.getWidth(),
						canvas_.getHeight(), BufferedImage.TYPE_INT_RGB);
				ImageComponent2D readImage = new ImageComponent2D(
						ImageComponent.FORMAT_RGB, bimageRead);

				// Raster for reading
				raster = new Raster(new Point3f(0.0f, 0.0f, 0.0f),
						Raster.RASTER_COLOR, 0, 0, bimageRead.getWidth(),
						bimageRead.getHeight(), readImage, null
				//readDepthFloat
				//readDepthInt
				);
				// Raster.setCapability(Raster.ALLOW_DEPTH_COMPONENT_READ);
				raster.setCapability(Raster.ALLOW_IMAGE_READ);

				canvas_.getGraphicsContext3D().readRaster(raster);
				// Read color info
				BufferedImage image = raster.getImage().getImage();

				// Set file name
				if (imageChooser_ == null) {
					imageChooser_ = new JFileChooser();

					ExampleFileFilter filter = new ExampleFileFilter();
					filter.addExtension("png");
					filter.setDescription("PNG Images");
					imageChooser_.setFileFilter(filter);
				}
				if (imageChooser_.showSaveDialog(manager_.getFrame()) == JFileChooser.APPROVE_OPTION) {
					//write file 
					File file = imageChooser_.getSelectedFile();
					try {
						javax.imageio.ImageIO.write(image, "PNG", file);
					} catch (IOException ex) {
						new WarningDialog(manager_.getFrame(), "", MessageBundle
								.get("message.ioexception")).showModalDialog();
					}
				}

				/*
				 //offScreen way
				 //kkkkkkkkkkkkkkkkkkk
				 //Set file name
				 if(imageChooser_ == null){
				 imageChooser_ = new JFileChooser();

				 ExampleFileFilter filter = new ExampleFileFilter();
				 filter.addExtension("png");
				 filter.setDescription("PNG Images");
				 imageChooser_.setFileFilter(filter);
				 }
				 
				 if(imageChooser_.showSaveDialog(GUIManager.this) == JFileChooser.APPROVE_OPTION ){
				 //Set view point
				 recordingMgr_ = RecordingManager.getInstance();
				 recordingMgr_.setView(viewer_[currentViewer_].getDrawable());
				 // Camera
				 ImageCamera camera = recordingMgr_.getImageCamera();
				 // Set size
				 Dimension d = viewer_[currentViewer_].getCanvas3D().getSize();
				 camera.setSize(d.width,d.height);
				 // get image
				 java.awt.image.BufferedImage image = camera.getImage();
				 // write file
				 File file = imageChooser_.getSelectedFile();
				 try{
				 javax.imageio.ImageIO.write(image,"PNG",file);
				 }catch(IOException ex){
				 new WarningDialog(
				 GUIManager.this,
				 "",
				 MessageBundle.get("message.ioexception")
				 ).showModalDialog();
				 }
				 }
				 */

			}
		});

		GUIAction.OBJECT_TRANSLATION.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setModelUpdate(false);
				behaviorManager.setOperationMode(BehaviorManager.OBJECT_TRANSLATION_MODE);
				objectToolBar_.setMode(ObjectToolBar.OBJECT_MODE);
				lblMode_.setText("[ EDIT : Translate Object ]");
			}
		});

		GUIAction.OBJECT_ROTATION.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setModelUpdate(false);
				behaviorManager.setOperationMode(BehaviorManager.OBJECT_ROTATION_MODE);
				objectToolBar_.setMode(ObjectToolBar.OBJECT_MODE);
				lblMode_.setText("[ EDIT : Rotate Object ]");
			}
		});
		GUIAction.JOINT_ROTATION.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setModelUpdate(false);
				behaviorManager.setOperationMode(BehaviorManager.JOINT_ROTATION_MODE);
				objectToolBar_.setMode(ObjectToolBar.OBJECT_MODE);
				lblMode_.setText("[ EDIT : Move Joint ]");
			}
		});

		GUIAction.FITTING_SRC.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				objectToolBar_.setMode(ObjectToolBar.FITTING_MODE);
				viewToolBar_.setEnabled(true);
				behaviorManager.setOperationMode(BehaviorManager.FITTING_FROM_MODE);
				lblMode_.setText("[ EDIT : Object Placement Select ]");
			}
		});

		GUIAction.FITTING_DEST.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				objectToolBar_.setMode(ObjectToolBar.FITTING_MODE);
				viewToolBar_.setEnabled(true);
				behaviorManager.setOperationMode(BehaviorManager.FITTING_TO_MODE);
				lblMode_.setText("[ EDIT : Object Placement Destination ]");
			}
		});

		GUIAction.DO_FIT.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setModelUpdate(false);
				behaviorManager.fit();
				objectToolBar_.selectNone();
				objectToolBar_.setMode(ObjectToolBar.OBJECT_MODE);
				viewToolBar_.setEnabled(true);
				behaviorManager.setOperationMode(BehaviorManager.OPERATION_MODE_NONE);
				lblMode_.setText("[ VIEW ]");
			}
		});

		GUIAction.INV_KINEMA_FROM.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				objectToolBar_.setMode(ObjectToolBar.INV_KINEMA_MODE);
				behaviorManager.setOperationMode(BehaviorManager.INV_KINEMA_FROM_MODE);
				lblMode_.setText("[ EDIT : Inverse Kinematics Base Link ]");
			}
		});

		GUIAction.INV_KINEMA_TRANS.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setModelUpdate(false);
				objectToolBar_.setMode(ObjectToolBar.INV_KINEMA_MODE);
				behaviorManager.setOperationMode(BehaviorManager.INV_KINEMA_TRANSLATION_MODE);
				lblMode_.setText("[ EDIT : Inverse Kinematics Translate ]");
			}
		});

		GUIAction.INV_KINEMA_ROT.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setModelUpdate(false);
				objectToolBar_.setMode(ObjectToolBar.INV_KINEMA_MODE);
				behaviorManager.setOperationMode(BehaviorManager.INV_KINEMA_ROTATION_MODE);
				lblMode_.setText("[ EDIT : Inverse Kinematics Rotate ]");
			}
		});

		GUIAction.OPERATION_DISABLE.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setModelUpdate(true);
				behaviorManager.setOperationMode(BehaviorManager.OPERATION_MODE_NONE);
				objectToolBar_.setMode(ObjectToolBar.OBJECT_MODE);
				viewToolBar_.setEnabled(true);
				lblMode_.setText("[ VIEW ]");
			}
		});
	}
	
	private void setModelUpdate(boolean b) {
		for (int i=0; i<currentModels_.size(); i++)
			currentModels_.get(i).update_ = b;
	}
	
	private boolean chkFileName(StringBuffer strBuffer){
	    boolean ret = true;
        if ( strBuffer.length() == 0 ) {
            JOptionPane.showMessageDialog(manager_.getFrame(), "Input file name.");
            ret = false;
        } else {
            if ( ! strBuffer.substring(0).matches( ".*\\." + RecordingDialog.EXTEND_ + "$") ){
                strBuffer.append(  "." + RecordingDialog.EXTEND_ );
            }
            if (!fileOverwriteDialog(strBuffer.substring(0))) {
                ret = false;
            }
        }
        return ret;
	}
}