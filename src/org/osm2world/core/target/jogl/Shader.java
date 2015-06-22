package org.osm2world.core.target.jogl;

import static javax.media.opengl.GL.GL_REPEAT;
import static javax.media.opengl.GL.GL_TEXTURE0;
import static javax.media.opengl.GL.GL_TEXTURE1;
import static javax.media.opengl.GL.GL_TEXTURE2;
import static javax.media.opengl.GL.GL_TEXTURE3;
import static javax.media.opengl.GL.GL_TEXTURE_2D;
import static javax.media.opengl.GL.GL_TEXTURE_WRAP_S;
import static javax.media.opengl.GL.GL_TEXTURE_WRAP_T;
import static javax.media.opengl.GL2GL3.GL_CLAMP_TO_BORDER;
import static javax.media.opengl.GL2GL3.GL_TEXTURE_BORDER_COLOR;
import static org.osm2world.core.target.common.material.Material.multiplyColor;
import static org.osm2world.core.target.common.material.Material.Transparency.BINARY;
import static org.osm2world.core.target.common.material.Material.Transparency.TRUE;
import static org.osm2world.core.target.jogl.AbstractJOGLTarget.getFloatBuffer;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import org.osm2world.core.target.common.TextureData;
import org.osm2world.core.target.common.TextureData.Wrap;
import org.osm2world.core.target.common.lighting.GlobalLightingParameters;
import org.osm2world.core.target.common.material.Material;
import org.osm2world.core.target.common.material.Material.Transparency;

import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.util.PMVMatrix;
import com.jogamp.opengl.util.texture.Texture;

public class Shader {
	
	/** maximum number of texture layers any material can use */
	public static final int MAX_TEXTURE_LAYERS = 4;
	
	private int vertexShader;
	private int fragmentShader;
	private int shaderProgram;
	private int projectionMatrixID;
	private int modelViewMatrixID;
	private int modelViewProjectionMatrixID;
	private int normalMatrixID;
	private GL3 gl;
	
	public Shader(GL3 gl) {
		this.gl = gl;
		shaderProgram = gl.glCreateProgram();

		if (shaderProgram < 1)
			throw new RuntimeException("Unable to create shader program.");

		/*
		 * set up the vertex and fragment shaders. use static methods
		 * for setting up the shaders. These methods return the unique int
		 * identifiers GL assigns to each shader
		 */
		vertexShader = createVertShader(gl, "/shaders/simple.vertex");
		fragmentShader = createFragShader(gl, "/shaders/simple.fragment");
		System.out.printf("Vertex: %d, Fragment: %d\n",vertexShader,fragmentShader);

		// attach the shaders to the shader program and link
		gl.glAttachShader(shaderProgram, vertexShader);
		gl.glAttachShader(shaderProgram, fragmentShader);
		
		// bind named attributes in shader to attribute index
		gl.glBindAttribLocation (shaderProgram, getVertexPositionID(), "VertexPosition");
		//gl.glBindAttribLocation (shaderProgram, getVertexColorID(), "VertexColor");
		gl.glBindAttribLocation (shaderProgram, getVertexNormalID(), "VertexNormal");
		gl.glBindAttribLocation (shaderProgram, getVertexTexCoordID(), "VertexTexCoord");

		gl.glLinkProgram(shaderProgram);
		// validate linking
		IntBuffer linkStatus = IntBuffer.allocate(1);
		gl.glGetProgramiv(shaderProgram, GL3.GL_LINK_STATUS, linkStatus);
		if (linkStatus.get() == GL.GL_FALSE) {
			Shader.printProgramInfoLog(gl, shaderProgram);
			throw new RuntimeException("could not link shader");
		}
		Shader.printProgramInfoLog(gl, shaderProgram);
		
		// get indices of uniform variables
		projectionMatrixID = gl.glGetUniformLocation(shaderProgram, "ProjectionMatrix");
		modelViewMatrixID = gl.glGetUniformLocation(shaderProgram, "ModelViewMatrix");
		modelViewProjectionMatrixID = gl.glGetUniformLocation(shaderProgram, "ModelViewProjectionMatrix");
		normalMatrixID = gl.glGetUniformLocation(shaderProgram, "NormalMatrix");

		// tell GL to validate the shader program and grab the created log
		gl.glValidateProgram(shaderProgram);
		// perform general validation that the program is usable
		IntBuffer validateStatus = IntBuffer.allocate(1);
		gl.glGetProgramiv(shaderProgram, GL3.GL_VALIDATE_STATUS, validateStatus);
		if (validateStatus.get() == GL.GL_FALSE) {
			Shader.printProgramInfoLog(gl, shaderProgram);
			throw new RuntimeException("could not validate shader");
		}
		Shader.printProgramInfoLog(gl, shaderProgram);
	}
	
	public void loadDefaults() {
		
		// set default material values
		gl.glUniform3f(gl.glGetUniformLocation(shaderProgram, "Material.Ka"), 0,0,0);
		gl.glUniform3f(gl.glGetUniformLocation(shaderProgram, "Material.Kd"), 0,0,0);
		gl.glUniform3f(gl.glGetUniformLocation(shaderProgram, "Material.Ks"), 0,0,0);
		gl.glUniform1f(gl.glGetUniformLocation(shaderProgram, "Material.Shininess"), 0);
	}
	
	/**
	 * Send uniform matrices "ProjectionMatrix, ModelViewMatrix and ModelViewProjectionMatrix" to vertex shader
	 * @param pmvMatrix
	 */
	public void setPMVMatrix(PMVMatrix pmvMatrix) {
		gl.glUniformMatrix4fv(this.getProjectionMatrixID(), 1, false, pmvMatrix.glGetPMatrixf());
		gl.glUniformMatrix4fv(this.getModelViewMatrixID(), 1, false, pmvMatrix.glGetMvMatrixf());
		FloatBuffer pmvMat = FloatBuffer.allocate(16);
		FloatUtil.multMatrixf(pmvMatrix.glGetPMatrixf(), pmvMatrix.glGetMvMatrixf(), pmvMat);
		gl.glUniformMatrix4fv(this.getModelViewProjectionMatrixID(), 1, false, pmvMat);
		gl.glUniformMatrix4fv(this.getNormalMatrixID(), 1, false, pmvMatrix.glGetMvitMatrixf());
	}
	
	public void setGlobalLighting(GlobalLightingParameters lighting) {
		
		gl.glUniform4f(gl.glGetUniformLocation(shaderProgram, "Light.Position"), (float)lighting.lightFromDirection.getX(),
				(float)lighting.lightFromDirection.getY(), -(float)lighting.lightFromDirection.getZ(), 0f);
		gl.glUniform3fv(gl.glGetUniformLocation(shaderProgram, "Light.La"), 1, getFloatBuffer(lighting.globalAmbientColor));
		gl.glUniform3fv(gl.glGetUniformLocation(shaderProgram, "Light.Ld"), 1, getFloatBuffer(lighting.lightColorDiffuse));
		gl.glUniform3fv(gl.glGetUniformLocation(shaderProgram, "Light.Ls"), 1, getFloatBuffer(lighting.lightColorSpecular));
	}
	
	public void setMaterial(Material material, JOGLTextureManager textureManager) {

		int numTexLayers = 0;
		if (material.getTextureDataList() != null) {
			numTexLayers = material.getTextureDataList().size();
		}
		
		/* set lighting */
		
		// TODO:
//		if (material.getLighting() == Lighting.SMOOTH) {
//			gl.glShadeModel(GL_SMOOTH);
//		} else {
//			gl.glShadeModel(GL_FLAT);
//		}
		
		/* set color */
		
		if (numTexLayers == 0 || material.getTextureDataList().get(0).colorable) {
			
			gl.glUniform3fv(gl.glGetUniformLocation(shaderProgram, "Material.Ka"), 1, getFloatBuffer(material.ambientColor()));
			gl.glUniform3fv(gl.glGetUniformLocation(shaderProgram, "Material.Kd"), 1, getFloatBuffer(material.diffuseColor()));
			gl.glUniform3f(gl.glGetUniformLocation(shaderProgram, "Material.Ks"), 1f, 1f, 1f);
			gl.glUniform1f(gl.glGetUniformLocation(shaderProgram, "Material.Shininess"), 100f);
			
		} else {
			
			gl.glUniform3fv(gl.glGetUniformLocation(shaderProgram, "Material.Ka"), 1, getFloatBuffer(
					multiplyColor(Color.WHITE, material.getAmbientFactor())));
			gl.glUniform3fv(gl.glGetUniformLocation(shaderProgram, "Material.Kd"), 1, getFloatBuffer(
					multiplyColor(Color.WHITE, material.getDiffuseFactor())));
			gl.glUniform3f(gl.glGetUniformLocation(shaderProgram, "Material.Ks"), 1f,1f,1f);
			gl.glUniform1f(gl.glGetUniformLocation(shaderProgram, "Material.Shininess"), 100f);	
		}
		
		/* set textures and associated parameters */
	    gl.glUniform1i(gl.glGetUniformLocation(shaderProgram, "useTexture"), numTexLayers > 0 ? 1 : 0);
		if (numTexLayers > 0) {
			
			if (material.getTransparency() == Transparency.FALSE) {
				gl.glDisable(GL.GL_BLEND);
			} else {
				gl.glEnable(GL.GL_BLEND);
				gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
			}
			
			gl.glActiveTexture(getGLTextureConstant(0));
			TextureData textureData = material.getTextureDataList().get(0);
			Texture texture = textureManager.getTextureForFile(textureData.file, false);

			texture.bind(gl);
	        
			/* wrapping behavior */
	        
			int wrap = 0;
			
			switch (textureData.wrap) {
			case CLAMP: System.out.println("Warning: CLAMP is no longer supported. Using CLAMP_TO_BORDER instead."); wrap = GL_CLAMP_TO_BORDER; break;
			case REPEAT: wrap = GL_REPEAT; break;
			case CLAMP_TO_BORDER: wrap = GL_CLAMP_TO_BORDER; break;
			}
			
			gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
	        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
	        
	        
	        if (textureData.wrap == Wrap.CLAMP_TO_BORDER) {
	        	
	        	/* TODO: make the RGB configurable -  for some reason,
	        	 * it shows up in lowzoom even if fully transparent */
	        	gl.glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR,
	        			getFloatBuffer(new Color(1f, 1f, 1f, 0f)));
	        	
	        }
	        


	        int loc = gl.glGetUniformLocation(shaderProgram, "Tex1");
	        if (loc < 0) {
	        	throw new RuntimeException("Tex1 not found in shader program.");
	        }
	        gl.glUniform1i(loc, 0);
		}
	   
	}
	
	static final int getGLTextureConstant(int textureNumber) {
		switch (textureNumber) {
		case 0: return GL_TEXTURE0;
		case 1: return GL_TEXTURE1;
		case 2: return GL_TEXTURE2;
		case 3: return GL_TEXTURE3;
		default: throw new Error("programming error: unhandled texture number");
		}
	}
	
	public void useShader() {
		gl.glUseProgram(this.getProgram());
	}
	
	public void disableShader() {
		gl.glUseProgram(0);
	}
	
	public int getProgram() {
		return shaderProgram;
	}
	
	public int getVertexPositionID() {
		return 0;
	}
	
//	public int getVertexColorID() {
//		return 1;
//	}
	
	public int getVertexNormalID() {
		return 2;
	}
	
	public int getVertexTexCoordID() {
		return 3;
	}
	
	public int getProjectionMatrixID() {
		return projectionMatrixID;
	}
	
	public int getModelViewMatrixID() {
		return modelViewMatrixID;
	}
	
	public int getModelViewProjectionMatrixID() {
		return modelViewProjectionMatrixID;
	}
	
	public int getNormalMatrixID() {
		return normalMatrixID;
	}

	public void freeResources() {
	    gl.glDetachShader(shaderProgram, vertexShader);
	    gl.glDetachShader(shaderProgram, fragmentShader);
	    gl.glDeleteProgram(shaderProgram);
	    gl.glDeleteShader(vertexShader);
	    gl.glDeleteShader(fragmentShader);
	    System.out.printf("Deleted Vertex: %d, Fragment: %d\n",vertexShader,fragmentShader);
	    gl = null;
	}
	
	@Override
	protected void finalize() {
		freeResources();
	}
	
	/*
	 * createVertShader is handed a String defining where to find the file
	 * that contains the shader code. It creates and compiles the shader
	 * and returns a unique int that GL associates with it
	 */
	public static int createVertShader(GL3 gl, String filename) {

		// get the unique id
		int vertShader = gl.glCreateShader(GL3.GL_VERTEX_SHADER);
		if (vertShader == 0)
			throw new RuntimeException("Unable to create vertex shader.");

		// create a single String array index to hold the shader code
		String[] vertCode = new String[1];
		vertCode[0] = "";
		String line;

		// open the file and read the contents into the String array.
		try {
			InputStream stream = System.class.getResourceAsStream(filename);
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			while ((line = reader.readLine()) != null) {
				vertCode[0] += line + "\n";
			}
		} catch (Exception e) {
			throw new RuntimeException("Fail reading vertex shader",e);
		}

		// Associate the code string with the unique id
		gl.glShaderSource(vertShader, 1, vertCode, null);
		// compile the vertex shader
		gl.glCompileShader(vertShader);

		// acquire compilation status
		IntBuffer shaderStatus = IntBuffer.allocate(1);
		gl.glGetShaderiv(vertShader, GL3.GL_COMPILE_STATUS, shaderStatus);

		// check whether compilation was successful
		if (shaderStatus.get() == GL.GL_FALSE) {
			printShaderInfoLog(gl, vertShader);
			throw new IllegalStateException("compilation error for shader ["
					+ filename + "].");
		}
		printShaderInfoLog(gl, vertShader);

		// the int returned is now associated with the compiled shader
		return vertShader;
	}

	/*
	 * 
	 * Essentially the same as the vertex shader
	 */
	public static int createFragShader(GL3 gl, String filename) {

		int fragShader = gl.glCreateShader(GL3.GL_FRAGMENT_SHADER);
		if (fragShader == 0)
			return 0;

		String[] fragCode = new String[1];
		fragCode[0] = "";
		String line;

		try {
			InputStream stream = System.class.getResourceAsStream(filename);
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			while ((line = reader.readLine()) != null) {
				fragCode[0] += line + "\n";
			}
		} catch (Exception e) {
			throw new RuntimeException("Fail reading fragment shader",e);
		}

		gl.glShaderSource(fragShader, 1, fragCode, null);
		gl.glCompileShader(fragShader);
		
		// acquire compilation status
		IntBuffer shaderStatus = IntBuffer.allocate(1);
		gl.glGetShaderiv(fragShader, GL3.GL_COMPILE_STATUS, shaderStatus);

		// check whether compilation was successful
		if (shaderStatus.get() == GL.GL_FALSE) {
			printShaderInfoLog(gl, fragShader);
			throw new IllegalStateException("compilation error for shader ["
					+ filename + "].");
		}
		printShaderInfoLog(gl, fragShader);

		return fragShader;
	}
	
	/*
	 * Prints the log to System.out
	 */
	public static boolean printShaderInfoLog(GL3 gl, int shader) {
		IntBuffer ival = IntBuffer.allocate(1);
		gl.glGetShaderiv(shader, GL3.GL_INFO_LOG_LENGTH,
				ival);

		int size = ival.get();
		if (size > 1) {
			ByteBuffer log = ByteBuffer.allocate(size);
			ival.flip();
			gl.glGetShaderInfoLog(shader, size, ival, log);
			byte[] infoBytes = new byte[size];
			log.get(infoBytes);
			System.out.println("Info log: " + new String(infoBytes));
			return true;
		}
		return false;
	 }
	
	/*
	 * Prints the log to System.out
	 */
	public static boolean printProgramInfoLog(GL3 gl, int prog) {
		IntBuffer ival = IntBuffer.allocate(1);
		gl.glGetProgramiv(prog, GL3.GL_INFO_LOG_LENGTH,
				ival);

		int size = ival.get();
		if (size > 1) {
			ByteBuffer log = ByteBuffer.allocate(size);
			ival.flip();
			gl.glGetProgramInfoLog(prog, size, ival, log);
			byte[] infoBytes = new byte[size];
			log.get(infoBytes);
			System.out.println("Info log: " + new String(infoBytes));
			return true;
		}
		return false;
	 }
}