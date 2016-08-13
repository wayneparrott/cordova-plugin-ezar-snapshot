/**
 * ezar.js
 * Copyright 2015, ezAR Technologies
 * Licensed under a modified MIT license, see LICENSE or http://ezartech.com/ezarstartupkit-license
 * 
 * @file Implements the ezar api for controlling device cameras, 
 *  zoom level and lighting. 
 * @author @wayne_parrott, @vridosh, @kwparrott
 * @version 0.1.0 
 */

var exec = require('cordova/exec'),
    argscheck = require('cordova/argscheck'),
    utils = require('cordova/utils');

module.exports = (function() {
           
	 //--------------------------------------
    var _snapshot = {};

    /**
     * Create a screenshot image
     *
     * options = {
     *   "name": null, only applies if saveToPhotoGallery is true
     *   "saveToPhotoGallery": true, 
     *   "encoding": _snapshot.ImageEncoding.JPEG 
     *   "quality": 100,  only applys for jpeg encoding
     *   "scale": 100,
     *   "includeCameraView": true,
     *   "includeWebView": true}
     */
    
    _snapshot.snapshot = function(successCallback,errorCallback, options) {
                
        argscheck.checkArgs('FFO', 'ezar.snapshot', arguments);        

        //options impl inspired by cordova Camera plugin
        options = options || {};
        var encoding = argscheck.getValue(options.encoding, _snapshot.ImageEncoding.JPEG);
        var quality = argscheck.getValue(options.quality, 50);
        quantity = Math.max(0, Math.min(100,quality));
        quality = quality == 0 ? 50 : quality;
        var scale = argscheck.getValue(options.scale, 100);
        scale = Math.max(0, Math.min(100,scale));
        scale = scale == 0 ? 100 : scale;
        var saveToPhotoGallery = options.saveToPhotoGallery === undefined ? true : !!options.saveToPhotoGallery;
        var imageName = argscheck.getValue(options.name, "");
        var includeCameraView = options.includeCameraView === undefined ? true : !!options.includeCameraView;
        var includeWebView = options.includeWebView === undefined ? true : !!options.includeWebView;
        
        //deprecated saveToPhotoAlbum but temp support for 2016
        saveToPhotoGallery = options.saveToPhotoAlbum === undefined ? saveToPhotoGallery : !!options.saveToPhotoAlbum;
                  
        var onSuccess = function(imageData) {
            var encoding = encoding == _snapshot.ImageEncoding.JPEG ? 
                _snapshot.ImageEncoding.JPEG : _snapshot.ImageEncoding.PNG;
            var dataUrl = "data:image/" + encoding + ";base64," + imageData;
            if (successCallback) {
                  successCallback(dataUrl);
            }
        };
                  
        exec(onSuccess,
             errorCallback,
             "snapshot",
             "snapshot",
            [encoding, quality, scale, saveToPhotoGallery, imageName, includeCameraView, includeWebView]);

    }

     _snapshot.saveToPhotoGallery = function(imageData,successCallback,errorCallback,options) {
                
        argscheck.checkArgs('SFF', 'ezar.saveToPhotoGallery', arguments); 

        var pattern = /^data:image\/(.{1,3});(base64,)/;
        var searchResult = pattern.exec(imageData);
        if (!searchResult) {
            if (errorCallback) {
                errorCallback('ImageData is not in base64 image format');
                return;
            }
        }
        
        var startIdx = searchResult[0].length;  //'data:image/xxx;base64,'.length;
        imageData = imageData.substring(startIdx);

        //options impl inspired by cordova Camera plugin
        options = options || {};
        var encoding = argscheck.getValue(options.encoding, _snapshot.ImageEncoding.JPEG);
        var quality = argscheck.getValue(options.quality, 50);
        quantity = Math.max(0, Math.min(100,quality));
        quality = quality == 0 ? 50 : quality;
        var scale = argscheck.getValue(options.scale, 100);
        scale = Math.max(0, Math.min(100,scale));
        scale = scale == 0 ? 100 : scale;
        var saveToPhotoGallery = true;
        var imageName = argscheck.getValue(options.name, "");

        exec(successCallback,
             errorCallback,
             "snapshot",
             "saveToPhotoGallery",
             [imageData, encoding, quality, scale, saveToPhotoGallery, imageName]);
     }
                  
    _snapshot.ImageEncoding = {
        JPEG: 0,             // Return JPEG encoded image
        PNG: 1               // Return PNG encoded image
    };

    
    return _snapshot;
    
}());

