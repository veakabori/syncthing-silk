// Copyright (C) 2014 The Syncthing Authors.
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this file,
// You can obtain one at http://mozilla.org/MPL/2.0/.

package syncthing.android.service;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import timber.log.Timber;

/**
 * Provides direct access to the config.xml file in the file system.
 *
 * This class should only be used if the syncthing API is not available (usually during startup).
 */
public class ConfigXml {

    public static final String CONFIG_FILE = "config.xml";

    private File mConfigFile;

    private Document mConfig;

    private ConfigXml(File configFile) {
        mConfigFile = configFile;
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            mConfig = db.parse(mConfigFile);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ConfigXml get(Context context) {
        File configfile = getConfigFile(context);
        if (!configfile.exists()) {
            return null;
        } else {
            return new ConfigXml(configfile);
        }
    }

    public static File getConfigFile(Context context) {
        return new File(SyncthingUtils.getConfigDirectory(context), CONFIG_FILE);
    }

    /**
     * Updates the config file.
     */
    public void updateIfNeeded() {
        Timber.d("Checking for needed config updates");
        boolean changed = false;
        Element options = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("options").item(0);

        //disable start browser
        NodeList startBrowser = options.getElementsByTagName("startBrowser");
        if (startBrowser != null && startBrowser.getLength() == 1) {
            if (Boolean.parseBoolean(startBrowser.item(0).getTextContent())) {
                Timber.d("Set 'startBrowser' to false");
                startBrowser.item(0).setTextContent(Boolean.toString(false));
                changed = true;
            }
        }

        NodeList folders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < folders.getLength(); i++) {
            Element r = (Element) folders.item(i);
            // Set ignorePerms attribute.
            if (!r.hasAttribute("ignorePerms") ||
                    !Boolean.parseBoolean(r.getAttribute("ignorePerms"))) {
                Timber.d("Set 'ignorePerms' on folder " + r.getAttribute("id"));
                r.setAttribute("ignorePerms", Boolean.toString(true));
                changed = true;
            }
            // Set rescanIntervalS attribute.
            if (Integer.parseInt(r.getAttribute("rescanIntervalS")) == 60) {
                Timber.d("Set 'rescanIntervalS' on folder " + r.getAttribute("id"));
                r.setAttribute("rescanIntervalS", "86400");
                changed = true;
            }
        }

        if (changed) {
            saveChanges();
        }
    }

    /**
     * Change default folder id to camera and path to camera folder path.
     */
    public void changeDefaultFolder() {
        Element folder = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("folder").item(0);
        folder.setAttribute("id", Build.MODEL + " Camera");
        folder.setAttribute("path", Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        folder.setAttribute("ro", "true");
        saveChanges();
    }

    /**
     * Writes updated mConfig back to file.
     */
    private void saveChanges() {
        try {
            Timber.d("Writing updated config back to file");
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(mConfig);
            StreamResult streamResult = new StreamResult(mConfigFile);
            transformer.transform(domSource, streamResult);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

}
