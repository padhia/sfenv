{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs { inherit system; };

        nativeDeps = with pkgs; [
          boehmgc
          clang
          libunwind
          llvm
          zlib
          pkg-config
          libfyaml
          pkl
        ];

        # Fixed-output derivation: downloads all Coursier/Mill dependencies
        # with network access and caches them for offline use in the build.
        #
        # To update the hash after dependency changes:
        #   1. Set outputHash to pkgs.lib.fakeHash
        #   2. Run: nix build .#packages.<system>.default
        #   3. Copy the "got:" hash from the error into outputHash below
        millDeps = pkgs.stdenv.mkDerivation {
          name = "sfenv-mill-deps";
          src = ./.;

          nativeBuildInputs = [
            pkgs.mill
            pkgs.jdk
            pkgs.cacert
            pkgs.coursier
          ]
          ++ nativeDeps;

          outputHash = "sha256-2iTRhdVLL89eKcdIikaw/Z4ukILP5qwwR1uFFqgIL1I=";
          outputHashAlgo = "sha256";
          outputHashMode = "recursive";

          buildPhase = ''
            # On macOS the JVM reads user.home from the OS rather than $HOME,
            # so we must override it via JAVA_TOOL_OPTIONS.
            mkdir -p $PWD/home
            export HOME=$PWD/home
            export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
            export COURSIER_CACHE=$HOME/coursier
            export COURSIER_ARCHIVE_CACHE=$HOME/coursier-arc
            # Project and transitive Maven deps.
            mill --no-server __.prepareOffline

            # Mill's internal infrastructure JARs (Scala Native worker, tools,
            # test-runner) are resolved lazily and bypass COURSIER_CACHE on macOS
            # via NSHomeDirectory().  cs fetch --cache constructs a FileCache
            # bound solely to COURSIER_CACHE, so the download is forced there
            # regardless of what any system-level Coursier cache contains.
            # This ensures the FOD output is identical on macOS and Linux.
            cs fetch --cache $HOME/coursier \
              'com.lihaoyi:mill-libs-scalanativelib-worker-0.5_3:1.1.7' \
              'org.scala-native:tools_2.13:0.5.12' \
              'org.scala-native:test-runner_2.13:0.5.12'
          '';

          installPhase = ''
            cp -r $HOME/coursier $out
          '';
        };

        sfenv = pkgs.stdenv.mkDerivation {
          pname = "sfenv";
          version = "0.3.0-RC3";
          src = ./.;

          nativeBuildInputs = [
            pkgs.mill
            pkgs.jdk
            pkgs.which
          ]
          ++ nativeDeps;

          buildPhase = ''
            mkdir -p $PWD/home
            export HOME=$PWD/home
            export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"

            # Restore pre-fetched Coursier cache (must be writable)
            cp -r ${millDeps} $HOME/coursier
            chmod -R u+w $HOME/coursier
            export COURSIER_CACHE=$HOME/coursier
            mkdir -p $HOME/coursier-arc
            export COURSIER_ARCHIVE_CACHE=$HOME/coursier-arc

            export CC="${pkgs.clang}/bin/clang"
            export CXX="${pkgs.clang}/bin/clang++"
            export LLVM_BIN="${pkgs.clang}/bin"
            export LIBFYAML_LIBS="-L${pkgs.libfyaml}/lib -lfyaml"

            mill --no-server nativeLink
          '';

          installPhase = ''
            mkdir -p $out/bin
            cp out/nativeLink.dest/out $out/bin/sfenv
          '';
        };
      in
      {
        packages.default = sfenv;

        devShells.default = pkgs.mkShell {
          name = "scala-native";

          buildInputs = nativeDeps;

          shellHook = ''
            export CC="${pkgs.clang}/bin/clang"
            export CXX="${pkgs.clang}/bin/clang++"
            export LLVM_BIN="${pkgs.clang}/bin"
            export LIBFYAML_LIBS="-L${pkgs.libfyaml}/lib -lfyaml"
            echo "⚡ Scala Native + Mill environment loaded!"
          '';
        };
      }
    );
}
