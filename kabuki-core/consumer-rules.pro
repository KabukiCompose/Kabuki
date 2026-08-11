# Kabuki core - consumer rules.
#
# onScreen<T>() instantiates screens reflectively through a public no-arg
# constructor (see instantiateScreen). If the module that contains the tests
# is minified, R8 may strip those constructors.
-keep class * extends kabuki.Screen {
    public <init>();
}
-keep class * extends kabuki.Component {
    public <init>();
}
