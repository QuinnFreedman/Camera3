./gradlew :camera3:clean && \
./gradlew :camera3:assembleRelease && \
./gradlew :camera3:bintrayUpload && \
python -mwebbrowser https://bintray.com/login?forwardedFrom=%2Fquinnfreedman%2Fcom.avalancheevantage.android
