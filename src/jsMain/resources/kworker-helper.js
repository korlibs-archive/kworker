self.onmessage = function(e) {
	if (e.data.type == 'import') {
        importScripts.apply(self, e.data.urls);
	} else if (e.data.type == 'eval') {
		eval(e.data.script);
	}
};
