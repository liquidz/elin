FROM nixos/nix
RUN nix-channel --update && \
        nix-env -iA \
          nixpkgs.babashka \
          nixpkgs.clj-kondo \
          nixpkgs.nodejs \
          nixpkgs.neovim
# (OPTIONAL) Install Clojure and OpenJDK
# RUN nix-env -iA nixpkgs.openjdk_headless nixpkgs.clojure
COPY ./ /root/.config/nvim/pack/elin/start/elin
RUN ln -s /root/.config/nvim/pack/elin/start/elin/minimal_config.vim /root/.config/nvim/init.vim
