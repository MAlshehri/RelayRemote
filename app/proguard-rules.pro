# protobuf-javalite stores generated field names in message metadata and looks
# them up reflectively. Keep only the generated DataStore fields; all other app
# code remains eligible for R8 shrinking, optimization, and obfuscation.
-keepclassmembers class io.coreback.relayremote.data.proto.** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
