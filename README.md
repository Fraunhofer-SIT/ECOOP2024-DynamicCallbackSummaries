Dynamically Generating Callback Summaries for Enhancing Static Analysis

ECOOP submission number for the paper: 69

## Quick-start guide (Kick-the-tires phase)
### Running the fast evaluation
For the fast evaluation, the approach is simple:
Build the docker container and run it, e.g. using

```
docker run --rm -it $(docker build -q .) /bin/bash Code/RQ-FastEval.sh
```






## Overview: What does the artifact comprise?

Please list for each distinct component of your artifact:

- Code for the scaled-down evaluation
Type: Code
Format: Source Code (Java)
Location: Code/ReplicateNumbers

- Code to automatically generate artifically apps as mentioned in RQ1
Type: Code
Format: Source Code (Java)
Location: Code/CallbackGeneration

- Code for the FlowDroid evaluation
Type: Code
Format: Source Code (Java)
Location: Code/JobSubmissionTool

- Code for test cases
Type: Code
Format: Source Code (Java)
Location: Code/AndroidStudioProjects

- Manually annotated results of the dynamic analysis (Summary)
Type: Data; Summaries consisting of edges; each edge is either false positive, incomplete or correct
Format: virtualedges - xml
Location: Results/Summaries/virtualedgesummaries-dcid-complete-annotated.xml

- Manually annotated Edgeminer summaries
Type: Data; Summaries consisting of edges; each edge is either false positive, incomplete or correct
Format: virtualedges - xml
Location: Results/Summaries/edgeminer-annotated.xml

- Results
Type: Data; Results for different RQs - partially precomputed
Format: various
Location: Results/

## License

We want to publish the artifact under LGPL2

## Complete Evaluation
For the expensive evaluation, the process is a bit more elaborate.
We used a specialized Eclipse distribution (CodeInspect-Development) which can be used to develop VUSC plug-ins. We obtained this along with VUSC from Fraunhofer SIT. We provide the eclipse project of our VUSC-Plugin used to generate Callbacks, which can only be build using this special Eclipse build environment, since it has dependencies to VUSC components in order to manage communication between the app and the computer and as an instrumentation framework. 

### Data Set:
Note that the dataset is only needed for a complete evaluation including the dynamic analysis and FlowDroid.
We used the dataset from AndroZoo (Allix et al, ACM MSR, IEEE, 2016) as a data source for our apps. Due to licensing restrictions, we cannot share the raw apps, but we provide md5 sums of the used apps in ```Datasets/*-subset-md5.txt``` corresponding to each research question that uses real-world apps. ```Datasets/*-subset.txt``` correspond to the files containing absolute file names to the actual apps and are used by the evaluation scripts for the large, expensive evals. The paths are anonymized.

### Verifying claims:

* 6.2: Baseline over the Dataset & obtaining the summaries from RQ1 & Generate RQ1 numbers [very expensive]

  The paragraph "Baseline over the Dataset" uses the data set for RQ1 (```Datasets/RQ1-CallbackGeneration-subset.txt``` is needed). You will also need an Android SDK with ```android.jar``` files in the ```platforms/platform-NUMBER``` directory, with the Number standing for a SDK version. We have the Android sdk 34 installed. In order to regenerate the summaries, the expensive dynamic analysis using has to be performed by using VUSC.
  In order to compile our Plugins, VUSC is needed since we have compile dependencies on VUSC plugins.
  In CodeInspect-Development, import the Plugins from ```Code/CallbackAnalysis```. (Note that since CodeInspect-Development does not come with a maven plugin, you can either install that or use a different Eclipse if you want to modify the ```ReplicateNumbers```, ```JobSubmission``` or ```CallbackCodeGeneration``` projects).
  We have provided screenshots in the ```VUSC``` folder which show how the launch configuration should be configured. Adjust ```-Xmx``` on the arguments tab to a good amount of your (free) system memory. For development, we used
```  -Dorg.eclipse.swt.graphics.Resource.reportNonDisposed=true -Xlog:jni+resolve=off
  -Xmx16g
--add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/java.util.regex=ALL-UNNAMED --add-opens=java.xml/com.sun.org.apache.xerces.internal.dom=ALL-UNNAMED
```
  The program arguments must include the path to the config file using ```--configfile PATH-To-SERVER-server.conf```. We have attached one at ```VUSC/server.conf.needsadaption```. Make sure to change the values in there according to the instructions inside.
  Once VUSC is started, you need to enter your license on ```http://127.0.0.1:18080/settings/license-management``` and set-up a device on the Device Management on ```http://127.0.0.1:18080/settings/device-management```. Select ```Android device``` as device type. We really recommend to use a real device as we found emulators to be rather sluggish (and consume quite a bit of system memory themselves). The serial number can be found using ```adb get-serialno``` (use the adb that lies within the Android SDK referenced in the server.conf). Please do not use your private phone due to security concerns (we do not recommend installing random apps). We cannot redistribute the Android SDK due to their terms of use (https://developer.android.com/studio/terms).
  For the test data set, we set the ```Dynamic Analysis Runtime Seconds``` to 15 seconds.

  Another way of launching the callback runner is by using the VUSC analysis server (and not the development IDE). Note that also there the ```/etc/ci/server.conf``` file should be modified to use part of our config in ```Content/VUSC/server.conf.needsadaption``` after the VUSC installation. The easiest way is to export the plugin from the development IDE is to export the feature. This can be done via the menu: Select File/Export.../Deployable plug-ins and fragments and select the both reproduce.* plugins. Export the feature to a directory, for example ```/tmp/abc```. 
  Run ```sudo ci installplugin file:///tmp/abc reproduce.callbackevaluation``` in order install the plugin. Then run ```ci restart``` (make sure you adapted the config, otherwise, many unrelated analysis will run and not the right one).

  When "Device with ID 1 doesn't exist" appears in the log file during the analysis, the device has not been configured. Make sure your device is unlocked when using the dynamic analysis.

  If you do not have the data set, you can use test cases from ```Datasets/TestAPKs/``` (the code for these is in ```Code/AndroidStudioProjects/```). In the folder you can also find the ground truth as returned by the tool.

  The code in ```Code/CallbackAnalysis/Evaluation``` is used to generate the numbers for RQ1. 

* RQ4, RQ5, RQ7: Generating all the numbers 

  #### Optional: Recompute precomputed results (dataset needed)
  Note that the implementation checks whether precomputed results are available in ```Results/RQ4-TransferFunctionsPrevalence```. We provide these precomputed results so that the raw dataset is not needed in order to compute the values from the paper. If you want to recompute those results, make sure that you have valid paths to all apps in the ```Datasets/RQ4-PrevalenceTransferFunctions-subset.txt``` file. Then delete the contents of the folder ```Results/RQ4-TransferFunctionsPrevalence```.

  #### Optional: Rerun FlowDroid evaluation (dataset needed, expensive)
  We already provide precomputed FlowDroid results, thus this step is optional.

  In order to recompute the FlowDroid evaluation, the data set ```Datasets/RQ7-FlowDroidClientAnalysis-subset.txt``` is needed, which we cannot redistribute, but is obtainable from AndroZoo (Allix et al, ACM MSR, IEEE, 2016).
  When you are using the docker container,
  you can mount the path to the data set, in our example below we have the dataset in in ```/opt/dataset``` on the host computer.
  You also need to have the Android SDK installed, which must have at least one ```android.jar``` in the ```platforms``` directory.
  ```docker run --rm -v path-to-android-sdk-linux/platforms:/opt/android-sdk-linux/platforms -v /opt/dataset:/path/to/ -it $(docker build -q .) /bin/bash```
  In the resulting bash prompt, make sure that ```ls /path/to/appinventor.ai_blasetaze.ScottPilgrim.apk``` returns a file. Then run ```/bin/bash Code/RQ7-FlowDroidEval.sh```. The script will take days to complete. Since the script file explictly specifies 250 GB heap size, Java will terminate without results when you do not have enough RAM.
  When the script is complete, do not exit the bash as this would delete all results, but run the evaluation directly in the same docker container:
  ```/bin/bash Code/RQ-FastEval.sh```
    

  #### Getting the values
  Run the evaluation:
  ```docker run --rm -it $(docker build -q .) /bin/bash Code/RQ-FastEval.sh```

  The values in the text are realized using LaTeX commands. These commands with the concrete values are created while running the fast evaluation.
  The data in "Table 1" and "Table 2" is also generated during the run.
  The docker container runs the Code present in ```Code/ReplicateNumbers/```, which prints out the values in standard output.
  You can search in the output for
  ```*** RQ 4: Prevalence of transfer functions ***```
  ```*** RQ 5: Comparison with EdgeMiner ***```
  ```*** RQ 7: FlowDroid client analysis ***```

