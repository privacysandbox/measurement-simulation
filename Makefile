SHELL = /bin/bash
OUT_DIR ?= out

.PHONY: all clean validator
all: validator

validator: $(OUT_DIR)/validate-headers.html $(OUT_DIR)/validate-headers.js

$(OUT_DIR)/validate-headers.html: header-validation/index.html $(OUT_DIR)
	@ cp $< $@

$(OUT_DIR)/validate-headers.js: header-validation/dist/main.js $(OUT_DIR)
	@ cp $< $@

$(OUT_DIR):
	@ mkdir -p $@

header-validation/dist/main.js: header-validation/package.json header-validation/src/*.js
	@ npm ci --prefix ./header-validation
	@ npm run build --prefix ./header-validation

clean:
	@ rm -rf $(OUT_DIR)

