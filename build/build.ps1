#build snapshot-walk.jar
#
# .\build.ps1

$jar = "snapshot-xwalk.jar"
$outdir = "out"

#clean up
Write-Host "    Clean build location"
# delete .\out directory
If ((Test-Path $outdir) -eq $true) {   
    rm -Force -Recurse -Confirm:$false $outdir
} 

mkdir $outdir

Write-Host "    Compile and jarring files"
#compile XWalkGetBitmapCallbackImpl class
$libs = "./libs/mockable-android-23.jar;./libs/xwalk-classes.jar;./libs/cordovalib.jar;./libs/snapshot.zip"
$classes = "..\src\android\XWalkGetBitmapCallbackImpl.java"
javac -cp $libs -d $outdir $classes

#create jar file
cd $outdir
jar cvf $jar *
cd ..

Write-Host "    Mv jar to Android src directory"
#copy jar to src
Copy-Item $outdir\$jar ..\src\android