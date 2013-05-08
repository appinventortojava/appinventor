package com.google.appinventor.buildserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.logging.Logger;

public class ZipUtil {
    private static final Logger LOG = Logger.getLogger(ProjectBuilder.class.getName());

	public static void recursiveZip(File dir, URI dirBase, ZipOutputStream out) throws IOException {
		File[] files = dir.listFiles();
		byte[] tmpBuf = new byte[1024];

		for (File file : files) {
			if (file.isDirectory()) {
				out.putNextEntry(new ZipEntry(dirBase.relativize(file.toURI()).getPath()));
				LOG.info("*********" + dirBase.relativize(file.toURI()).getPath() + " *******");
				out.closeEntry();
				recursiveZip(file,dirBase,out);
				continue;
			}
			
			FileInputStream in = new FileInputStream(file.getAbsolutePath());
			out.putNextEntry(new ZipEntry(dirBase.relativize(file.toURI()).getPath()));
			//out.putNextEntry(new ZipEntry(file.toURI().relativize(dirBase).getPath()+file.getName()));
			LOG.info("******** out " + dirBase.toString());
			LOG.info("*********  out for loop" + dirBase.relativize(file.toURI()).getPath() + " *******");

			int len;
			while ((len = in.read(tmpBuf)) != -1) {
				out.write(tmpBuf, 0, len);
			}

			out.closeEntry();
			in.close();
		}
	}
		
		public static void recursiveZipInDir(File dir, URI dirBase, URI path, ZipOutputStream out) throws IOException {
    		File[] files = dir.listFiles();
    		byte[] tmpBuf = new byte[1024];

    		for (File file : files) {
    			if (file.isDirectory()) {
    				out.putNextEntry(new ZipEntry(dirBase.relativize(path).getPath() + file.getName()));
    				LOG.info("*********dir" + dirBase.relativize(file.toURI()).getPath() + " *******");
    				out.closeEntry();
    				recursiveZip(file,dirBase,out);
    				continue;
    			}

    			FileInputStream in = new FileInputStream(file.getAbsolutePath());
    			out.putNextEntry(new ZipEntry(dirBase.relativize(path).getPath() + file.getName()));
    			//out.putNextEntry(new ZipEntry(file.toURI().relativize(dirBase).getPath()+file.getName()));
    			LOG.info("********dir out " + dirBase.toString());
    			LOG.info("*********dir  out for loop" + dirBase.relativize(file.toURI()).getPath() + " *******");

    			int len;
    			while ((len = in.read(tmpBuf)) != -1) {
    				out.write(tmpBuf, 0, len);
    			}

    			out.closeEntry();
    			in.close();
    		}
	}

}
