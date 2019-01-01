self.onmessage = function(e) {
	if (e.data.type == 'import') {
		importScripts(e.data.url);
	} else if (e.data.type == 'eval') {
		eval(e.data.script);
	}
};

