/*
 * CDVezARSnapshot.m
 *
 * Copyright 2016, ezAR Technologies
 * http://ezartech.com
 *
 * By @wayne_parrott
 *
 * Licensed under a modified MIT license. 
 * Please see LICENSE or http://ezartech.com/ezarstartupkit-license for more information
 *
 */
 
#import "CDVezARSnapshot.h"
#import "MainViewController.h"

//NSString *const EZAR_ERROR_DOMAIN = @"EZAR_SNAPSHOT_ERROR_DOMAIN";
NSInteger const EZAR_CAMERA_VIEW_TAGx = 999;

#ifndef __CORDOVA_4_0_0
#import <Cordova/NSData+Base64.h>
#endif

//copied from cordova camera plugin
static NSString* toBase64(NSData* data) {
    SEL s1 = NSSelectorFromString(@"cdv_base64EncodedString");
    SEL s2 = NSSelectorFromString(@"base64EncodedString");
    SEL s3 = NSSelectorFromString(@"base64EncodedStringWithOptions:");
    
    if ([data respondsToSelector:s1]) {
        NSString* (*func)(id, SEL) = (void *)[data methodForSelector:s1];
        return func(data, s1);
    } else if ([data respondsToSelector:s2]) {
        NSString* (*func)(id, SEL) = (void *)[data methodForSelector:s2];
        return func(data, s2);
    } else if ([data respondsToSelector:s3]) {
        NSString* (*func)(id, SEL, NSUInteger) = (void *)[data methodForSelector:s3];
        return func(data, s3, 0);
    } else {
        return nil;
    }
}

@implementation CDVezARSnapshot
{
   
}


// INIT PLUGIN - does nothing atm
- (void) pluginInitialize
{
    [super pluginInitialize];
}

- (UIImageView *) getCameraView
{
    UIImageView* cameraView = (UIImageView *)[self.viewController.view viewWithTag: EZAR_CAMERA_VIEW_TAGx];
    return cameraView;
}

- (BOOL) isVideoOverlayAvailable
{
    return [self getCameraView] == nil;
}

-(CDVPlugin*)getVideoOverlayPlugin
{
    MainViewController *ctrl = (MainViewController *)self.viewController;
    CDVPlugin* videoOverlayPlugin = [ctrl.pluginObjects objectForKey:@"CDVezARVideoOverlay"];
    return videoOverlayPlugin;
}

-(BOOL) hasVideoOverlayPlugin
{
    return !![self getVideoOverlayPlugin];
}

-(UIColor *) getBackgroundColor
{
    UIColor * result = NULL;
    
    if (![self hasVideoOverlayPlugin]) {
        return result;
    }
    
    // Find background color
    NSString* methodName = @"getBackgroundColor";
    SEL selector = NSSelectorFromString(methodName);
    result = (UIColor *)[[self getVideoOverlayPlugin] performSelector:selector];
    
    return result;
}

-(BOOL) isCameraRunning
{
    BOOL result = NO;
    
    if (![self hasVideoOverlayPlugin]) {
        return result;
    }

    // Find AVCaptureSession
    NSString* methodName = @"isCameraRunning";
    SEL selector = NSSelectorFromString(methodName);
    result = (BOOL)[[self getVideoOverlayPlugin] performSelector:selector];
    
    return result;
}

-(BOOL) isFrontCameraRunning
{
    BOOL result = NO;
    
    if (![self hasVideoOverlayPlugin]) {
        return result;
    }
    
    // Find AVCaptureSession
    NSString* methodName = @"isFrontCameraRunning";
    SEL selector = NSSelectorFromString(methodName);
    result = (BOOL)[[self getVideoOverlayPlugin] performSelector:selector];
    
    return result;
}

- (AVCaptureStillImageOutput *) getAVCaptureStillImageOutput
{
    if (![self hasVideoOverlayPlugin]) {
        return nil;
    }
    
    NSString* methodName = @"getAVCaptureStillImageOutput";
    SEL selector = NSSelectorFromString(methodName);
    AVCaptureStillImageOutput *stillImageOutput =
        (AVCaptureStillImageOutput *)[[self getVideoOverlayPlugin] performSelector:selector];
    
    return stillImageOutput;
}


// rely on videoOverlay to setup the AVCaptureStillImageOutput when the camera starts. This gives
// the camera time to adjust its light level & white balance; otherwise you get a very dark
// snapshot image if we grab the video frame too quickly.
- (void) snapshot:(CDVInvokedUrlCommand*)command
{
    BOOL includeCameraView =
        [[command argumentAtIndex:5 withDefault:@(YES)] boolValue] &&
        [self hasVideoOverlayPlugin];
    
    
    // Find the videoOverlay CameraView
    UIImageView *cameraView = self.getCameraView;
    if (!includeCameraView || !cameraView) {
        //capture only WebView
       [self snapshotViewHierarchy:nil cameraView:nil playSound:YES command:command];
        return;
    }
    
    AVCaptureStillImageOutput* stillImageOutput = [self getAVCaptureStillImageOutput];
    
    if (!stillImageOutput || ![self isCameraRunning]) {
        [self snapshotViewHierarchy:nil cameraView:nil playSound:YES command:command];
        return;
    }
 
    
    //
    AVCaptureConnection *videoConnection = nil;
    for (AVCaptureConnection *connection in stillImageOutput.connections) {
        for (AVCaptureInputPort *port in [connection inputPorts]) {
            if ([[port mediaType] isEqual:AVMediaTypeVideo] ) {
                videoConnection = connection;
                break;
            }
        }
        if (videoConnection) { break; }
    }
    
    //Capture video frame as image. The image will not include the webview content.
    //Temporarily set the cameraView image to the video frame (a jpg) long enough
    //to capture the entire view hierarcy as an image.
    //
    //NSLog(@"about to request a capture from: %@", stillImageOutput);
    [stillImageOutput captureStillImageAsynchronouslyFromConnection: videoConnection
                       completionHandler: ^(CMSampleBufferRef imageBuffer, NSError *error) {
                        
        if (error) {
            
            NSDictionary* errorResult = [self makeErrorResult: 1 withError: error];
                                                          
            CDVPluginResult* pluginResult =
                [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                messageAsDictionary: errorResult];
                                                          
            return  [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
               
                                                      
        NSData *imageData = [AVCaptureStillImageOutput jpegStillImageNSDataRepresentation:imageBuffer];
        UIImage *cameraImage = [[UIImage alloc] initWithData:imageData];
                         
        UIInterfaceOrientation interfaceOrientation = self.viewController.interfaceOrientation;
        if ([self isFrontCameraRunning]) {
            if (interfaceOrientation == UIInterfaceOrientationLandscapeLeft) {
                interfaceOrientation = UIInterfaceOrientationLandscapeRight;
            } else if (interfaceOrientation == UIInterfaceOrientationLandscapeRight) {
                interfaceOrientation = UIInterfaceOrientationLandscapeLeft;
            }
        }
                           
        //rotate image to match device orientation
        switch (interfaceOrientation) {
            case UIInterfaceOrientationPortrait:
                cameraImage = [UIImage imageWithCGImage: [cameraImage CGImage] scale:1.0 orientation:UIImageOrientationRight];
                break;
            case UIInterfaceOrientationLandscapeLeft:
                cameraImage = [UIImage imageWithCGImage: [cameraImage CGImage] scale:1.0 orientation:UIImageOrientationDown];
                break;
            case UIInterfaceOrientationPortraitUpsideDown:
                cameraImage = [UIImage imageWithCGImage: [cameraImage CGImage] scale:1.0 orientation:UIImageOrientationLeft];
                break;
            case UIInterfaceOrientationLandscapeRight:
                cameraImage = [UIImage imageWithCGImage: [cameraImage CGImage] scale:1.0 orientation:UIImageOrientationUp];
                break;
            
        }
        
        [self snapshotViewHierarchy:cameraImage cameraView:cameraView playSound:NO command:command];
        
    }];
}


- (void) snapshotViewHierarchy:(UIImage*)cameraImage cameraView:(UIImageView*)cameraView 
            playSound:(BOOL)shouldPlaySound command:(CDVInvokedUrlCommand*)command
{
    EZAR_IMAGE_ENCODING encodingType = [[command argumentAtIndex:0 withDefault:@(EZAR_IMAGE_ENCODING_JPG)] unsignedIntegerValue];
    
    CGFloat quality = [[command argumentAtIndex:1 withDefault:@(100)] unsignedIntegerValue] / 100.0;
    CGFloat scale = [[command argumentAtIndex:2 withDefault:@(100)] unsignedIntegerValue] / 100.0;
    BOOL saveToPhotoAlbum = [[command argumentAtIndex:3 withDefault:@(YES)] boolValue];
    BOOL includeWebView = [[command argumentAtIndex:6 withDefault:@(YES)] boolValue];
    
    //Assign the video frame image to the cameraView image.
    //The cameraView.contentMode = UIViewContentModeScaleAspectFill
    //if (cameraImage) {
        cameraView.image = cameraImage;
    //}
    
    if (shouldPlaySound) {
        //solution from http://stackoverflow.com/questions/5430949/play-iphone-camera-shutter-sound-when-i-click-a-button
        AudioServicesPlaySystemSound(1108);
    }
    
    UIImage* screenshot = NULL;
    
    if (includeWebView) {
        //capture the entire view hierarchy
        UIView *view = self.viewController.view;
        UIGraphicsBeginImageContextWithOptions(view.bounds.size, YES, 0);
        CGContextRef context = UIGraphicsGetCurrentContext();
        CGContextSetFillColorWithColor(context,[[UIColor greenColor] CGColor]);
        [view drawViewHierarchyInRect: view.bounds afterScreenUpdates: YES];
        screenshot = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
    } else if (cameraImage) {
        screenshot = cameraImage;
    } else {
        scale = 1.0;
        //create an empty image
        UIView *view = self.viewController.view;
        CGRect rect = view.bounds;
        UIGraphicsBeginImageContext(rect.size);
        CGContextRef context = UIGraphicsGetCurrentContext();
        CGContextSetFillColorWithColor(context,[[UIColor blackColor] CGColor]);
        CGContextFillRect(context, rect);
        screenshot = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
    }
    
    if (scale < 1.0) {
        CGSize s1 = screenshot.size;
        CGSize sz = CGSizeApplyAffineTransform(screenshot.size, CGAffineTransformMakeScale(scale,scale));
        UIGraphicsBeginImageContextWithOptions(sz, NO, 0.0);
        CGRect rect = CGRectMake(0, 0, sz.width, sz.height);
        [screenshot drawInRect: rect] ;
        screenshot = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
    }
    
    //clear camera view image
    cameraView.image = nil;
    
    if (saveToPhotoAlbum) { //save image to gallery
        //todo: handling error saving to photo gallery
        UIImageWriteToSavedPhotosAlbum(screenshot, nil, nil, nil);
    }
    
    //format image for return
    NSData *screenshotData = nil;
    if (encodingType == EZAR_IMAGE_ENCODING_JPG) {
        screenshotData = UIImageJPEGRepresentation(screenshot, quality);
    } else {
        screenshotData = UIImagePNGRepresentation(screenshot);
    }
    
    CDVPluginResult* pluginResult =
    [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:
     toBase64(screenshotData)];
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) saveToPhotoGallery:(CDVInvokedUrlCommand*)command {
    NSString* imageDataString = [command argumentAtIndex:0];
    EZAR_IMAGE_ENCODING encodingType = [[command argumentAtIndex:1 withDefault:@(EZAR_IMAGE_ENCODING_JPG)]  unsignedIntegerValue];
    CGFloat quality = [[command argumentAtIndex:2 withDefault:@(100)] unsignedIntegerValue] / 100.0;
    CGFloat scale = [[command argumentAtIndex:3 withDefault:@(100)] unsignedIntegerValue] / 100.0;

    // NSData from the Base64 encoded str
    NSData *imageData =
        [[NSData alloc] initWithBase64EncodedString:imageDataString options:0];
    UIImage *image = [UIImage imageWithData:imageData];
    
    //scale image if needed
    if (scale < 1.0) {
        CGSize s1 = image.size;
        CGSize sz = CGSizeApplyAffineTransform(image.size, CGAffineTransformMakeScale(scale,scale));
        UIGraphicsBeginImageContextWithOptions(sz, NO, 0.0);
        CGRect rect = CGRectMake(0, 0, sz.width, sz.height);
        [image drawInRect: rect] ;
        image = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
    }

    //create a new image in order to apply quality and encode image 
    NSData *newImageData = nil;
    if (encodingType == EZAR_IMAGE_ENCODING_JPG) {
        newImageData = UIImageJPEGRepresentation(image, quality);
    } else {
        newImageData = UIImagePNGRepresentation(image);
    }
    UIImage *newImage = [UIImage imageWithData:imageData];

    //todo: handling error saving to photo gallery
    UIImageWriteToSavedPhotosAlbum(newImage, nil, nil, nil);
    
    CDVPluginResult* pluginResult =
        [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: @""];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

typedef NS_ENUM(NSUInteger, EZAR_ERROR_CODE) {
    EZAR_ERROR_CODE_ERROR=1,
    EZAR_ERROR_CODE_INVALID_ARGUMENT,
    EZAR_ERROR_CODE_INVALID_STATE,
    EZAR_ERROR_CODE_ACTIVATION
};

//
//
//
- (NSDictionary*)makeErrorResult: (EZAR_ERROR_CODE) errorCode withData: (NSString*) description
{
    NSMutableDictionary* errorData = [NSMutableDictionary dictionaryWithCapacity:4];
    
    [errorData setObject: @(errorCode)  forKey:@"code"];
    [errorData setObject: @{ @"description": description}  forKey:@"data"];
    
    return errorData;
}

//
//
//
- (NSDictionary*)makeErrorResult: (EZAR_ERROR_CODE) errorCode withError: (NSError*) error
{
    NSMutableDictionary* errorData = [NSMutableDictionary dictionaryWithCapacity:2];
    [errorData setObject: @(errorCode)  forKey:@"code"];
    
    NSMutableDictionary* data = [NSMutableDictionary dictionaryWithCapacity:2];
    [data setObject: [error.userInfo objectForKey: NSLocalizedFailureReasonErrorKey] forKey:@"description"];
    [data setObject: @(error.code) forKey:@"iosErrorCode"];
    
    [errorData setObject: data  forKey:@"data"];
    
    return errorData;
}

@end
