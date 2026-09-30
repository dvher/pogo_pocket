{
  description = "Pogo Pocket — Pogo sticky notes as an Android home-screen widget";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      buildToolsVersion = "35.0.0";
      ndkVersion = "27.2.12479018";
      composeSdk = emulator: pkgs.androidenv.composeAndroidPackages ({
        platformVersions = [ "35" ];
        buildToolsVersions = [ buildToolsVersion ];
        includeNDK = true;
        ndkVersions = [ ndkVersion ];
      } // pkgs.lib.optionalAttrs emulator {
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
      });
      shell = emulator:
        let
          android = composeSdk emulator;
          sdk = "${android.androidsdk}/libexec/android-sdk";
        in pkgs.mkShell {
          packages = with pkgs; [ go jdk17 gradle go-task android.androidsdk ];
          ANDROID_HOME = sdk;
          ANDROID_SDK_ROOT = sdk;
          ANDROID_NDK_HOME = "${sdk}/ndk/${ndkVersion}";
          JAVA_HOME = pkgs.jdk17.home;
          # The aapt2 that Gradle downloads is not a NixOS binary; use the SDK's.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildToolsVersion}/aapt2";
          shellHook = ''
            export PATH="$HOME/go/bin:$PATH"
          '';
        };
    in {
      devShells.${system} = {
        default = shell false;
        # Adds the emulator and an x86_64 Android 15 image (a few GB).
        emulator = shell true;
      };
    };
}
