let overlay = self: super: { jdk = super.jdk11; };
in with (import <nixpkgs> { overlays=[overlay]; });

mkShell {
  buildInputs = [ jdk gradle ];
}
