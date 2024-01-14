function! elin#script#empty_port(callback) abort
	let command = [
				\ g:elin#babashka,
				\ '--prn',
				\ '--eval',
				\ '(with-open [sock (java.net.ServerSocket. 0)] (.getLocalPort sock))',
				\ ]
  return elin#compat#job#redir(command, a:callback)
endfunction
