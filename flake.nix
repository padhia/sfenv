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
      in
      {
        devShell = pkgs.mkShell {
          name = "sfenv";
          buildInputs = [
            pkgs.graalvmPackages.graalvm-ce
          ];
          shellHook = ''
            # Set explicit environment variables for Java tooling compatibility
            export JAVA_HOME="${pkgs.graalvmPackages.graalvm-ce}"
            export GRAALVM_HOME="${pkgs.graalvmPackages.graalvm-ce}"

            echo "☕ GraalVM development environment loaded!"
            java -version
          '';
        };
      }
    );
}
