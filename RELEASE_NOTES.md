#ezAR Snapshot Plugin Release Notes

##1.0.0 (20170717)
1. Added package.json for Cordova 7 compliance.
2. Renamed to cordova-plugin-ezar-snapshot in preparation for publishing to npm


##0.2.7 (20170112)
1. android - fix black snapshot image when plugin used standalone or when videoOverlay cameraView is excluded.


##0.2.6 (20160915)
1. iOS - added NSPhotoLibraryUsageDescription to plist, required for iOS 10


##0.2.5 (20160901)
1. Android - added support for the [Crosswalk webview](https://crosswalk-project.org/). 


##0.2.4 (20160818)
1. Provide a custom name for the snapshot image when saved to the Android photo gallery (Android only). 
Added name option property for snapshot(successCB,errorCB,options).
2. Added new options to saveToPhotoGallery(imageBase64,successCB,errorCB,options). Options include:
name (Android only), quality, scale, ImageEncoding)


##0.2.3 (20160620)
1. New quality [0-100] and scale [0-100] options property for snapshot(successCB,errorCB,options). 
2. New saveToPhotoGallery(base64ImageData) function. Use this function along with the snapshot function 
to implement a preview-before-save UI for selfies or other images you capture in your app.


##0.2.2 (20160606)
1. Fixed distorted image on first snapshot for iOS devices. You must update the VideoOverlay plugin in your project to 0.2.4 or greater for 
this fix to take effect.
2. Added snapshot options 'includeCameraView' from the VideoOverlay plugin and  'includeWebView' to control the layers in the snapshot image.
3. When 'includeCameraView' is false, the snapshot image background is set to  the videoOverlay background property
4. A black image the size of the display is returned when both the cameraVieaw 
and webView are omitted.  


##0.2.1 (20160310)
1. Added camera shutter sound for cases when the video overlay is not installed or running on iOS devices.
2. Fixed iOS camera exception issue.


##0.2.0 (20160302)
Initial release of the snapshot functionality. Capture an image of the app user interface

This plugin works with or without the Flashlight and VideoOverlay plugins.

###Known Issues
1. Plugin error reporting needs improvement.
2. No camera shutter sound when the video overlay is not installed or running on iOS devices.

