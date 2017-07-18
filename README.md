# ezAR Snapshot Cordova Plugin
Capture images of the application user interface in JPG or PNG format. 
Optionally, save the image to the default photo gallery.

Works with or without the VideoOverlay plugin. When the VideoOverlay 
plugin is not installed or does not have an active camera then the 
snapshot image will only include the WebView content.

## Supported Platforms
- iOS 7 and greater
- Android 4.2 and greater 

## Getting Started
Add the snapshot plugin to your Corodva project the Cordova CLI

        cordova plugin add cordova-plugin-ezar-snapshot

Next in your Cordova JavaScript deviceready handler include the following 
JavaScript snippet to initialize ezAR and activate the camera on the 
back of the device.

        ezar.snapshot(
            function(base64Image) {
                //do something with the image
                },
            function(err) {
                alert('snapshot error: ' + err);
                },       
            {"saveToPhotoAlbum":true,
             "encoding": ezAR.ImageEncoding.PNG}
            )
                    
## Additional Documentation        
See [ezartech.com](http://ezartech.com) for documentation and support.

## License
The ezAR Snapshot is licensed under a [modified MIT license](http://www.ezartech.com/ezarstartupkit-license).


Copyright (c) 2015-2017, ezAR Technologies


