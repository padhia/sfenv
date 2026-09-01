{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    let
      overlays.default = final: prev: {
        sfenv = self.packages.${final.system}.default;
      };

      eachSystem =
        system:
        let
          pkgs = import nixpkgs { inherit system; };

          nativeBuildInputs = with pkgs; [
            boehmgc
            clang
            libunwind
            llvm
            zlib
            pkg-config
            which
            mill
            jdk
          ];
          buildInputs = with pkgs; [ libfyaml ];
          allInputs = nativeBuildInputs ++ buildInputs;

          millBuild =
            dpeCachePkg:
            let
              copyCache = pkgs.lib.optionalString (dpeCachePkg != null) ''
                cp -r ${dpeCachePkg}/share/coursier $HOME/coursier
                chmod -R u+w $HOME/coursier  # coursier cache must be writable
              '';
            in
            ''
              runHook preBuild
              mkdir -p $PWD/home
              export HOME=$PWD/home
              export JAVA_HOME="${pkgs.jdk.home}"
              export JAVA_TOOL_OPTIONS="-Duser.home=$HOME" # On macOS the JVM reads user.home from the OS rather than \$HOME

              ${copyCache}
              export COURSIER_CACHE=$HOME/coursier

              mill --no-server --ticker false __.nativeLink
              runHook postBuild
            '';

          millDeps = pkgs.stdenv.mkDerivation {
            name = "sfenv-mill-deps";
            src = ./.;

            inherit nativeBuildInputs buildInputs;

            outputHash =
              {
                x86_64-linux = "sha256-GIEwv+oloFHNS0kyYREZq3P374K0+cqACOLxgpIK9vA=";
                aarch64-darwin = "sha256-GIEwv+oloFHNS0kyYREZq3P374K0+cqACOLxgpIK9vA=";
                aarch64-linux = "sha256-GIEwv+oloFHNS0kyYREZq3P374K0+cqACOLxgpIK9vA=";
              }
              .${system};
            outputHashAlgo = "sha256";
            outputHashMode = "recursive";

            buildPhase = millBuild null;

            installPhase = ''
              runHook preInstall
              mkdir -p $out/share
              cp -r $HOME/coursier $out/share/coursier
              runHook postInstall
            '';
          };

        in
        {
          devShells.default = pkgs.mkShell {
            name = "scala-native";
            buildInputs = allInputs;
          };

          packages.default = pkgs.stdenv.mkDerivation {
            pname = "sfenv";
            version = "0.3.0-RC4";
            src = ./.;

            inherit nativeBuildInputs buildInputs;

            buildPhase = millBuild millDeps;

            installPhase = ''
              runHook preInstall
              mkdir -p $out/bin
              cp out/nativeLink.dest/out $out/bin/sfenv
              runHook postInstall
            '';
          };
        };
    in
    {
      inherit overlays;
      inherit (flake-utils.lib.eachDefaultSystem eachSystem) packages devShells;
    };
}
