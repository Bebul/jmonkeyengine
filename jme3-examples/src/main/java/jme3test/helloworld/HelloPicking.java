/*
 * Copyright (c) 2009-2012 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jme3test.helloworld;

import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.AnimEventListener;
import com.jme3.animation.LoopMode;
import com.jme3.app.SimpleApplication;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.DirectionalLight;
import com.jme3.light.SpotLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import static com.jme3.math.FastMath.sin;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.CompareMode;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.shadow.SpotLightShadowRenderer;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.WrapMode;
import com.jme3.util.SkyFactory;
import java.util.Random;
import static jme3test.light.TestDirectionalLightShadow.SHADOWMAP_SIZE;

/** Sample 8 - how to let the user pick (select) objects in the scene 
 * using the mouse or key presses. Can be used for shooting, opening doors, etc. */
public class HelloPicking extends SimpleApplication {
  
  Spatial golem;
  private AnimChannel channel;
  private AnimControl control;
  Node player;
  Node characters;
  boolean charactersAttached = false;

  public static void main(String[] args) {
    HelloPicking app = new HelloPicking();
    app.start();
  }
  private Node shootables;
  private Geometry mark;
  DirectionalLightShadowRenderer dlsr;
  
  @Override
  public void simpleInitApp() {
    initCrossHairs(); // a "+" in the middle of the screen to help aiming
    initKeys();       // load custom key mappings
    initMark();       // a red sphere to mark the hit
    
    flyCam.setMoveSpeed(100f);

    /** create four colored boxes and a floor to shoot at: */
    shootables = new Node("Shootables");
    shootables.setShadowMode(ShadowMode.CastAndReceive);
    rootNode.attachChild(shootables);
    rootNode.attachChild(makeFloor());
    //shootables.attachChild(makeCharacter());
    
    // initialize cube materials
    materials = new Material[cubeCol.length];
    for (int i=0; i<materials.length; i++)
    {
      materials[i] = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
      materials[i].setColor("Color", cubeCol[i]);
    }
    
    characters = new Node("Characters");
    characters.setShadowMode(ShadowMode.Cast);
    rootNode.attachChild(characters);
    charactersAttached = true;
    
    shootables.attachChild(makeColumnHierarchy(2, 0f, 0.5f, 0f, 400, new Vector3f(-200,0,-200)));
    System.out.println(" Generated: " + cubes + " cubes!");
    
    player = (Node) assetManager.loadModel("Models/Ninja/Ninja.mesh.xml");
    player.setLocalScale(0.03f);
    player.setLocalTranslation(202.0f, -8f, 200f);

    player.setShadowMode(ShadowMode.CastAndReceive);
    characters.attachChild(player);
    control = player.getControl(AnimControl.class);
    control.addListener(new AnimEventListener() {
      public void onAnimCycleDone(AnimControl control, AnimChannel channel, String animName) {
        if (animName.equals("Backflip")) {
          /** After "walk", reset to "stand". */
          channel.setAnim("Walk", 0.50f);
          channel.setLoopMode(LoopMode.Loop);
          channel.setSpeed(1f);
        }
      }
      public void onAnimChange(AnimControl control, AnimChannel channel, String animName) {
        // unused
      }      
    });
    channel = control.createChannel();
    channel.setAnim("Walk");    
    
    characters.attachChild(makeCharacter());
    
    // We must add a light to make the model visible
    DirectionalLight sun = new DirectionalLight();
    sun.setDirection(new Vector3f(-1f, -1f, -1f));
    sun.setColor(ColorRGBA.White.mult(1.3f));
    rootNode.addLight(sun);
    
    Spatial sky = SkyFactory.createSky(assetManager, "Scenes/Beach/FullskiesSunset0068.dds", false);
    sky.setLocalScale(350);

    rootNode.attachChild(sky);
    
    dlsr = new DirectionalLightShadowRenderer(assetManager, SHADOWMAP_SIZE, 3);
    dlsr.setLight(sun);
    dlsr.setLambda(0.55f);
    dlsr.setShadowIntensity(0.6f);
    dlsr.setEdgeFilteringMode(EdgeFilteringMode.Nearest);
    //dlsr.displayDebug();
    //viewPort.addProcessor(dlsr);
  
 /*
    PointLight lamp_light = new PointLight();
    lamp_light.setColor(ColorRGBA.Yellow);
    lamp_light.setRadius(20f);
    lamp_light.setPosition(new Vector3f(210.0f, 0f, 210f));
    //lamp_light.setPosition(new Vector3f(10.0f, 40f, 10f));
    rootNode.addLight(lamp_light);
    
    PointLightShadowRenderer plsr = new PointLightShadowRenderer(assetManager, SHADOWMAP_SIZE);
    plsr.setLight(lamp_light);
    plsr.setShadowIntensity(0.6f);
    plsr.setEdgeFilteringMode(EdgeFilteringMode.Nearest);
    viewPort.addProcessor(plsr);
*/
    SpotLight spot = new SpotLight();
    spot.setSpotRange(200f);                           // distance
    spot.setSpotInnerAngle(15f * FastMath.DEG_TO_RAD); // inner light cone (central beam)
    spot.setSpotOuterAngle(35f * FastMath.DEG_TO_RAD); // outer light cone (edge of the light)
    spot.setColor(ColorRGBA.White.mult(1.3f));         // light color
    spot.setPosition(new Vector3f(192.0f, 0f, 192f));
    spot.setDirection(new Vector3f(1, -1, 1));
    rootNode.addLight(spot);
    
    SpotLightShadowRenderer slsr = new SpotLightShadowRenderer(assetManager, SHADOWMAP_SIZE);
    slsr.setLight(spot);
    slsr.setEdgeFilteringMode(EdgeFilteringMode.Nearest);
    slsr.setShadowIntensity(0.6f);
    viewPort.addProcessor(slsr);
  }

  /** Declaring the "Shoot" action and mapping to its triggers. */
  private void initKeys() {
    inputManager.addMapping("Shoot", new MouseButtonTrigger(MouseInput.BUTTON_LEFT)); // trigger 1: left-button click
    inputManager.addListener(actionListener, "Shoot");
    inputManager.addMapping("Backflip", new KeyTrigger(KeyInput.KEY_SPACE));
    inputManager.addListener(actionListener, "Backflip");
    inputManager.addMapping("OptimizeShadow", new KeyTrigger( KeyInput.KEY_N));
    inputManager.addListener(actionListener, "OptimizeShadow");
    inputManager.addMapping("DebugFrustum", new KeyTrigger( KeyInput.KEY_F));
    inputManager.addListener(actionListener, "DebugFrustum");
  }
  /** Defining the "Shoot" action: Determine what was hit and how to respond. */
  private ActionListener actionListener = new ActionListener() {

    public void onAction(String name, boolean keyPressed, float tpf) {
      if (name.equals("Shoot") && !keyPressed) {
        // 1. Reset results list.
        CollisionResults results = new CollisionResults();
        // 2. Aim the ray from cam loc to cam direction.
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        // 3. Collect intersections between Ray and Shootables in results list.
        shootables.collideWith(ray, results);
        // 4. Print the results
        System.out.println("----- Collisions? " + results.size() + "-----");
        for (int i = 0; i < results.size(); i++) {
          // For each hit, we know distance, impact point, name of geometry.
          float dist = results.getCollision(i).getDistance();
          Vector3f pt = results.getCollision(i).getContactPoint();
          String hit = results.getCollision(i).getGeometry().getName();
          System.out.println("* Collision #" + i);
          System.out.println("  You shot " + hit + " at " + pt + ", " + dist + " wu away.");
        }
        // 5. Use the results (we mark the hit object)
        if (results.size() > 0) {
          // The closest collision point is what was truly hit:
          CollisionResult closest = results.getClosestCollision();
          // Let's interact - we mark the hit with a red dot.
          mark.setLocalTranslation(closest.getContactPoint());
          rootNode.attachChild(mark);
          // remove all other cubes in the column
          Geometry cGeom = closest.getGeometry();
          if (cGeom != null)
          {
            Node p = cGeom.getParent();
            if (p!=null)
            {
              for (Spatial ch : p.getChildren())
              {
                if (ch!=cGeom)
                {
                  p.detachChild(ch);
                }
              }
              if (p.getParent()!=null && p.getParent().getName()=="TEST_BIG_COL")
              {
                if (charactersAttached) 
                  rootNode.detachChildNamed("Characters");
                else
                  rootNode.attachChild(characters);
                charactersAttached = !charactersAttached;
              }
            }
          }
        } else {
          // No hits? Then remove the red mark.
          rootNode.detachChild(mark);
        }
      }
      else if (name.equals("Backflip") && !keyPressed) {
        if (!channel.getAnimationName().equals("Backflip")) {
          /** Play the "walk" animation! */
          channel.setAnim("Backflip", 0.50f);
          channel.setLoopMode(LoopMode.Loop);
        }
      }
      else if (name.equals("OptimizeShadow") && !keyPressed)
      {
        RenderManager.optimizeRenderShadow = !RenderManager.optimizeRenderShadow;
        if ( RenderManager.optimizeRenderShadow ) System.out.println(" optimizeRenderShadow ON");
        else System.out.println(" optimizeRenderShadow OFF");
      }
      else if (name.equals("DebugFrustum") && !keyPressed)
      {
          dlsr.displayFrustum();
      }
    }
  };
  
  @Override
  public void simpleUpdate(float tpf) {
    // make the player rotate:
    golem.rotate(0, 2*tpf, 0); 
  }  

  /** A cube object for target practice */
  protected Geometry makeCube(String name, float x, float y, float z) {
    Box box = new Box(1, 1, 1);
    Geometry cube = new Geometry(name, box);
    cube.setLocalTranslation(x, y, z);
    Material mat1 = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat1.setColor("Color", ColorRGBA.randomColor());
    cube.setMaterial(mat1);
    
    cube.setShadowMode(ShadowMode.CastAndReceive);
    return cube;
  }

  /** A floor to show that the "shot" can go through several objects. */
  protected Geometry makeFloor() {
    Box box = new Box(220, .2f, 220);
    box.scaleTextureCoordinates(new Vector2f(10, 10));
    Geometry floor = new Geometry("the Floor", box);
    floor.setLocalTranslation(200, -9, 200);
    
    Material matGroundL = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
    Texture grass = assetManager.loadTexture("Textures/Terrain/splat/grass.jpg");
    grass.setWrap(WrapMode.Repeat);
    matGroundL.setTexture("DiffuseMap", grass);

    floor.setMaterial(matGroundL);
    floor.setShadowMode(ShadowMode.CastAndReceive);
    
    //Material mat1 = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    //mat1.setColor("Color", ColorRGBA.Gray);
    //floor.setMaterial(mat1);
    return floor;
  }

  /** A red ball that marks the last spot that was "hit" by the "shot". */
  protected void initMark() {
    Sphere sphere = new Sphere(30, 30, 0.2f);
    mark = new Geometry("BOOM!", sphere);
    Material mark_mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mark_mat.setColor("Color", ColorRGBA.Red);
    mark.setMaterial(mark_mat);
  }

  /** A centered plus sign to help the player aim. */
  protected void initCrossHairs() {
    //setDisplayStatView(false);
    guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
    BitmapText ch = new BitmapText(guiFont, false);
    ch.setSize(guiFont.getCharSet().getRenderedSize() * 2);
    ch.setText("+"); // crosshairs
    ch.setLocalTranslation( // center
      settings.getWidth() / 2 - ch.getLineWidth()/2, settings.getHeight() / 2 + ch.getLineHeight()/2, 0);
    guiNode.attachChild(ch);
  }

  protected Spatial makeCharacter() {
    // load a character from jme3test-test-data
    golem = assetManager.loadModel("Models/Oto/Oto.mesh.xml");
    golem.scale(0.5f);
    golem.setLocalTranslation(200.0f, -6f, 200f);
    golem.setShadowMode(ShadowMode.CastAndReceive);

    return golem;
  }
    
    private ColorRGBA[] cubeCol = { 
       new ColorRGBA(0x66/255f,0x99/255f,0,1f),
       new ColorRGBA(0x99/255f,0x99/255f,0,1f),
       new ColorRGBA(0xcc/255f,0x99/255f,0,1f),
       new ColorRGBA(0x99/255f,0x66/255f,0,1f),
       new ColorRGBA(0x66/255f,0x33/255f,0,1f),
       new ColorRGBA(0xff/255f,0xff/255f,0xcc/255f,1f),
       ColorRGBA.White
    };

    public Material[] materials;
    
    protected static int cubes = 0;
    protected Spatial makeColumn(float x, float y, float z, int levelsCount) {
        if (levelsCount<=0) return null;
        
        Node bigColumn = new Node("BigCol");
        bigColumn.setShadowMode(ShadowMode.Inherit);
        bigColumn.setLocalTranslation(x, y, z);
        //bigColumn.setCullHint(Spatial.CullHint.Always);
        Vector3f translate = new Vector3f(1.5f, 0, 0);
        Quaternion roll60 = new Quaternion(); 
        roll60.fromAngleAxis( FastMath.PI/3 , new Vector3f(0,1,0) );
        
        boolean useRed = false;
        //if (cubes<=10000 && cubes>(10000-6*levelsCount)) {
        if (cubes==0) {
          bigColumn.setName("TEST_BIG_COL");
          useRed = true;
        }

        for (int i=0; i<6; i++)
        {
          Node smallColumn = new Node("SmallCol"+i);
          smallColumn.setShadowMode(ShadowMode.Inherit);

          float boxSize = 0.5f;
          float h = 0;
          float scale = 1f;
          for (int j=0; j<levelsCount; j++)
          {
            boxSize *= scale;
            Box box = new Box(boxSize, boxSize, boxSize);
            String cubeName = useRed ? "redCube" : "box"+(10*i+j);
            Geometry cube = new Geometry(cubeName, box);
            cube.setShadowMode(ShadowMode.Inherit);
            cubes++;
            Material mat1;
            if (useRed) {
              mat1 = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
              mat1.setColor("Color", ColorRGBA.Red);
            }
            else mat1 = materials[j]; // reuse the same material for the "same" cubes
            cube.setMaterial(mat1);
            cube.move(translate/*.mult(scale)*/);
            cube.move(0,h,0); h += 2*boxSize;
            cube.rotate(0, FastMath.DEG_TO_RAD*i*60f, 0);
  
            scale *= 0.9f;
            smallColumn.attachChild(cube);
          }
          bigColumn.attachChild(smallColumn);

          //rotate about the Y-Axis by 60deg
          translate = roll60.mult(translate);
        }
        return bigColumn;
    }

    static Random randGen = new Random();
    protected Spatial makeColumnHierarchy(int recurseCount, float x, float y, float z, float size, Vector3f pos) {
        if (recurseCount==0)
        {
          // raise y to create "hat" sin(x2+z2)/(x2+z2)
          float arg = (pos.x*pos.x+pos.z*pos.z)/3000;
          float height = sin(arg);
          if (arg<0.001) height = 1f;
          else height = height/arg;
          height *= 40f;
          return makeColumn(x, y+height, z, 2+randGen.nextInt()%4); //-2 and -3 leads to no column shown
        }
        else
        {
          Node colGrp = new Node("ColGrp" + recurseCount + "-" + (int)(pos.x) + "-" + (int)(pos.z));
          colGrp.setShadowMode(ShadowMode.Inherit);
          colGrp.setLocalTranslation(x, y, z);
          recurseCount--;
          size = size/4;
          
          // 4x4 
          for (int ix=0; ix<4; ix++)
            for (int iz=0; iz<4; iz++)
            {
              Vector3f newPos = new Vector3f(pos.x+ix*size, pos.y, pos.z+iz*size);
              Spatial child = makeColumnHierarchy(recurseCount, ix*size, y, iz*size, size, newPos);
              if (child!=null)
                colGrp.attachChild(child);
            }
          //there was something probably added, if not, the collideWith should cope with getWorldBound()==null
          return colGrp;
        }
    }

}
