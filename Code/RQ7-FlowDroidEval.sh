#/bin/bash

cd /Code/JobSubmissionTool/
mvn -o package
cd /

# This script runs the FlowDroid eval

#First, the baseline
echo "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><virtualedges></virtualedges>" > virtualedges.xml
DIR="/Results/RQ7-FlowDroid/flowdroid-baseline/"
java  \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    -cp /Code/JobSubmissionTool/target/JobSubmissionTool-jar-with-dependencies.jar reproduction.dcidsubmit.FlowDroidRunner \
    -a Datasets/RQ7-FlowDroidClientAnalysis-subset.txt \
    -r SourcesSinks/UnconstrainedSourcesSinks.xml \
    -t "$DIR" \
    --heapspace 250g --parallel 1 --maxcores 20 -c 180 -m 900 \
    -p /opt/android-sdk-linux/platforms > >(tee -a "$DIR/stdout.log") 2> >(tee -a "$DIR/stderr.log" >&2)

#Edgeminer without param mapping
cp Results/Summaries/edgeminer-annotated.xml .
DIR="/Results/RQ7-FlowDroid/flowdroid-edgeminer/"
java  \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    -cp /Code/JobSubmissionTool/target/JobSubmissionTool-jar-with-dependencies.jar reproduction.dcidsubmit.FlowDroidRunner \
    -a Datasets/RQ7-FlowDroidClientAnalysis-subset.txt \
    -r SourcesSinks/UnconstrainedSourcesSinks.xml \
    -t "$DIR" \
    --heapspace 250g --parallel 1 --maxcores 20 -c 180 -m 900 \
    -p /opt/android-sdk-linux/platforms  > >(tee -a "$DIR/stdout.log") 2> >(tee -a "$DIR/stderr.log" >&2)

#Edgeminer with param mapping
cp Results/Summaries/edgeminer-annotated-with-parammappings.xml .
DIR="/Results/RQ7-FlowDroid/fd-with-parametermappings-edgeminer/"
java  \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    -cp /Code/JobSubmissionTool/target/JobSubmissionTool-jar-with-dependencies.jar reproduction.dcidsubmit.FlowDroidRunner \
    -a Datasets/RQ7-FlowDroidClientAnalysis-subset.txt \
    -r SourcesSinks/UnconstrainedSourcesSinks.xml \
    -t "$DIR" \
    --heapspace 250g --parallel 1 --maxcores 20 -c 180 -m 900 \
    -p /opt/android-sdk-linux/platforms  > >(tee -a "$DIR/stdout.log") 2> >(tee -a "$DIR/stderr.log" >&2)


#CGMiner without param mapping
cp Results/Summaries/virtualedgesummaries-dcid-complete-annotated.xml .
DIR="/Results/RQ7-FlowDroid/flowdroid-dcid/"
java  \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    -cp /Code/JobSubmissionTool/target/JobSubmissionTool-jar-with-dependencies.jar reproduction.dcidsubmit.FlowDroidRunner \
    -a Datasets/RQ7-FlowDroidClientAnalysis-subset.txt \
    -r SourcesSinks/UnconstrainedSourcesSinks.xml \
    -t "$DIR" \
    --heapspace 250g --parallel 1 --maxcores 20 -c 180 -m 900 \
    -p /opt/android-sdk-linux/platforms  > >(tee -a "$DIR/stdout.log") 2> >(tee -a "$DIR/stderr.log" >&2)


#CGMiner with param mapping
cp Results/Summaries/virtualedgesummaries-dcid-complete-annotated-with-parammappings.xml .
DIR="/Results/RQ7-FlowDroid/fd-with-parametermappings-dcid/"
java  \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    -cp /Code/JobSubmissionTool/target/JobSubmissionTool-jar-with-dependencies.jar reproduction.dcidsubmit.FlowDroidRunner \
    -a Datasets/RQ7-FlowDroidClientAnalysis-subset.txt \
    -r SourcesSinks/UnconstrainedSourcesSinks.xml \
    -t "$DIR" \
    --heapspace 250g --parallel 1 --maxcores 20 -c 180 -m 900 \
    -p /opt/android-sdk-linux/platforms  > >(tee -a "$DIR/stdout.log") 2> >(tee -a "$DIR/stderr.log" >&2)

