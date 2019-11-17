package com.gathergood.calum.ardemo

import android.app.PendingIntent.getActivity
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.*
import com.google.android.filament.Scene
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.collision.Box
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.collision.Sphere
import org.w3c.dom.Text
import com.google.ar.sceneform.rendering.Renderable




class MainActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private lateinit var editTextNote: EditText
    private lateinit var saveNote: Button
    private lateinit var InfoLayout: LinearLayout
    private lateinit var InfoText: TextView
    private lateinit var toggleCamera: FloatingActionButton

    private var infoCard: Node? = null

    private var isTracking: Boolean = false
    private var isHitting: Boolean = false

    private var setNote: Boolean = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)




        val modelurl = intent.getStringExtra("model_url")

        editTextNote= findViewById(R.id.editTextField)

        saveNote = findViewById(R.id.saveTextButton)



        arFragment = sceneform_fragment as ArFragment



        // Adds a listener to the ARSceneView
        // Called before processing each frame
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            arFragment.onUpdate(frameTime)
            onUpdate()
        }

        floatingActionButtonAddNote.setOnClickListener{
                setNote = true;
        }

        // Set the onclick lister for our button
        // Change this string to point to the .sfb file of your choice :)
        floatingActionButton.setOnClickListener { addObject(Uri.parse(modelurl))
            }



        showFab(false)



    }

    private fun addNoteOnClick(enabled: Boolean){
        if(enabled) {
            floatingActionButtonAddNote.isEnabled = true
            floatingActionButtonAddNote.visibility = View.VISIBLE
        } else {
            floatingActionButton.isEnabled = false
            floatingActionButton.visibility = View.GONE
        }

    }

    // Simple function to show/hide our FAB
    private fun showFab(enabled: Boolean) {
        if (enabled) {
            floatingActionButton.isEnabled = true
            floatingActionButton.visibility = View.VISIBLE
        } else {
            floatingActionButton.isEnabled = false
            floatingActionButton.visibility = View.GONE
        }
    }

    // Updates the tracking state
    private fun onUpdate() {
        updateTracking()
        // Check if the devices gaze is hitting a plane detected by ARCore
        if (isTracking) {
            val hitTestChanged = updateHitTest()
            if (hitTestChanged) {
                showFab(isHitting)
            }
        }

    }

    // Performs frame.HitTest and returns if a hit is detected
    private fun updateHitTest(): Boolean {
        val frame = arFragment.arSceneView.arFrame
        val point = getScreenCenter()
        val hits: List<HitResult>
        val wasHitting = isHitting
        isHitting = false
        if (frame != null) {
            hits = frame.hitTest(point.x.toFloat(), point.y.toFloat())
            for (hit in hits) {
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    isHitting = true
                    break
                }
            }
        }
        return wasHitting != isHitting
    }

    // Makes use of ARCore's camera state and returns true if the tracking state has changed
    private fun updateTracking(): Boolean {
        val frame = arFragment.arSceneView.arFrame
        val wasTracking = isTracking
        isTracking = frame.camera.trackingState == TrackingState.TRACKING
        return isTracking != wasTracking
    }

    // Simply returns the center of the screen
    private fun getScreenCenter(): Point {
        val view = findViewById<View>(android.R.id.content)
        return Point(view.width / 2, view.height / 2)
    }

    /**
     * @param model The Uri of our 3D sfb file
     *
     * This method takes in our 3D model and performs a hit test to determine where to place it
     */
    private fun addObject(model: Uri) {
        val frame = arFragment.arSceneView.arFrame
        val point = getScreenCenter()
        if (frame != null) {
            val hits = frame.hitTest(point.x.toFloat(), point.y.toFloat())
            for (hit in hits) {
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    placeObject(arFragment, hit.createAnchor(), model)
                    break
                }
            }
        }
    }

    /**
     * @param fragment our fragment
     * @param anchor ARCore anchor from the hit test
     * @param model our 3D model of choice (in this case from our remote url)
     *
     * Uses the ARCore anchor from the hitTest result and builds the Sceneform nodes.
     * It starts the asynchronous loading of the 3D model using the ModelRenderable builder.
     */
    private fun placeObject(fragment: ArFragment, anchor: Anchor, model: Uri) {
        ModelRenderable.builder()
                .setSource(fragment.context, RenderableSource.builder().setSource(
                        fragment.context,
                        model,
                        RenderableSource.SourceType.GLTF2)
                        .build())
                .setRegistryId(model)

                .build()
                .thenAccept {
                    addNodeToScene(fragment, anchor, it)
                }
                .exceptionally {
                    Toast.makeText(this@MainActivity, "Could not fetch model from $model", Toast.LENGTH_SHORT).show()
                    return@exceptionally null
                }
    }



    /**
     * @param fragment our fragment
     * @param anchor ARCore anchor
     * @param renderable our model created as a Sceneform Renderable
     *
     * This method builds two nodes and attaches them to our scene
     * The Anchor nodes is positioned based on the pose of an ARCore Anchor. They stay positioned in the sample place relative to the real world.
     * The Transformable node is our Model
     * Once the nodes are connected we select the TransformableNode so it is available for interactions
     */
    private fun addNodeToScene(fragment: ArFragment, anchor: Anchor, renderable: ModelRenderable) {

        val anchorNode = AnchorNode(anchor)



        // TransformableNode means the user to move, scale and rotate the model
        val transformableNode = TransformableNode(fragment.transformationSystem)
        transformableNode.renderable = renderable
        transformableNode.setParent(anchorNode)
        setAlpha(0.2f, transformableNode)

        val infoCard = Node()
        infoCard.setParent(transformableNode)
        infoCard.setEnabled(false)
        infoCard.setLocalPosition(Vector3(0f, 0f, 0f))


        ViewRenderable.builder()
                .setView(this, R.layout.test_view)
                .build()
                .thenAccept{ renderable ->
                    infoCard.setRenderable(renderable)
                    val textView = renderable.getView() as TextView
                    textView.setText("Cranial Trauma")
                }
                .exceptionally { throwable -> throw AssertionError("Could not load plane card view.", throwable) }
        transformableNode.setOnTouchListener { hitTestResult, motionEvent ->
            if(setNote) {
                Toast.makeText(this, "Model Tapped Note",
                        Toast.LENGTH_SHORT).show()

                editTextNote.visibility = View.VISIBLE;
                saveNote.visibility = View.VISIBLE;

                val frame = fragment.getArSceneView().getArFrame()
                for (hit in frame.hitTest(motionEvent)) {

                    buildRenderable(fragment, hit.createAnchor(), transformableNode, hitTestResult.point)
                    break
                }
                setNote = false;

            }
            else {
                null
            }
            true

        }

        fragment.arSceneView.scene.addChild(anchorNode)

        transformableNode.select()
    }

    private fun buildRenderable(fragment: ArFragment, anchor: Anchor, parent: TransformableNode, hit: Vector3) {
        ViewRenderable.builder()
                .setView(this, R.layout.test_view)
                .build()
                .thenAccept { renderable -> addTextToScene(fragment, anchor, renderable, parent, hit) }
                .exceptionally { throwable ->
                    val builder = AlertDialog.Builder(this)
                    builder.setMessage(throwable.message)
                            .setTitle("Codelab error!")
                    val dialog = builder.create()
                    dialog.show()
                    null
                }
    }

    private fun addTextToScene(fragment: ArFragment, anchor: Anchor, renderable: ViewRenderable, parent: TransformableNode, hit: Vector3) {

        val anchorNode = Node()
        renderable.renderPriority = 0
        anchorNode.setParent(parent)
        anchorNode.renderable = renderable
        val pos: Vector3

        if (parent.collisionShape is Box) {
            val box = parent.collisionShape as Box
            val size = box.getSize()
            pos = Vector3(size.x * 0.5f, size.y * 1f, size.z * 0f)
        } else {
            val sphere = parent.collisionShape as Sphere
            val diameter = sphere.radius * 2
            pos = Vector3(diameter * 0.5f, diameter * 1f, diameter * 0f)
        }

        anchorNode.worldPosition = hit

        val tv =  (anchorNode.getRenderable() as ViewRenderable).view.findViewById<TextView>(R.id.textInfoCard)
        saveNote.setOnClickListener(View.OnClickListener {
            Log.e("SHURT", editTextNote.text.toString());
            tv.setText(editTextNote.text)
            Log.e("SHURTT", tv.text.toString())


        })

    }

    fun setAlpha(alpha: Float, node: TransformableNode) {
        if (node.renderable is ViewRenderable) {
            (node.renderable as ViewRenderable).view.alpha = alpha
        } else {

                for (i in 0 until node.renderable.getSubmeshCount()) {
                    val m = node.renderable.getMaterial(i).makeCopy()
                    node.renderable.setMaterial(i, m)
                }

        }
    }



}
