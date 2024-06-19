SHELL = /bin/bash
OUT_DIR ?= out
OUT_DIR_SUB ?= out/dist

.PHONY: all clean validator

all: validator

$(OUT_DIR)/index.html: index.bs $(OUT_DIR)
	@ (HTTP_STATUS=$$(curl https://api.csswg.org/bikeshed/ \
                                --output $@ \
	                       --write-out '%{http_code}' \
	                       --header 'Accept: text/plain, text/html' \
	                       -F die-on=warning \
	                       -F file=@$<) && \
	[[ "$$HTTP_STATUS" -eq "200" ]]) || ( \
		echo ""; cat $@; echo ""; \
		rm $@; \
		exit 22 \
	);

validator: $(OUT_DIR)/validate-headers.html $(OUT_DIR)/style.css $(OUT_DIR)/dist/main.js

$(OUT_DIR):
	@ mkdir -p $@

$(OUT_DIR_SUB):
	@ mkdir -p $@

$(OUT_DIR)/validate-headers.html: header-validation/index.html $(OUT_DIR)
	@ cp $< $@

$(OUT_DIR)/style.css: header-validation/style.css $(OUT_DIR)
	@ cp $< $@

$(OUT_DIR_SUB)/main.js: header-validation/dist/main.js $(OUT_DIR_SUB)
	@ cp $< $@

header-validation/dist/main.js: header-validation/package.json header-validation/webpack.config.js header-validation/src/*.js
	@ npm ci --prefix ./header-validation
	@ npm run build --prefix ./header-validation

clean:
	@ rm -rf $(OUT_DIR)