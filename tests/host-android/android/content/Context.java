package android.content;
import java.io.*;
import java.nio.file.*;
public class Context {
 private final Path root;
 public Context(Path root){this.root=root;}
 public Context getApplicationContext(){return this;}
 public Context getAssets(){return this;}
 public InputStream open(String asset)throws IOException{return Files.newInputStream(root.resolve(asset));}
}
