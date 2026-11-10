# Navigation is an interface in WebKit 1.15 and a class in 1.16.
-keepclassmembers class androidx.webkit.Navigation {
    public java.lang.String getUrl();
    public boolean isSameDocument();
    public boolean isReload();
    public boolean isBack();
    public boolean isForward();
    public boolean didCommit();
    public boolean didCommitErrorPage();
}
