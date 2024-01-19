function! elin#internal#echom(text, highlight) abort
  execute 'echohl' a:highlight
	try
    for line in split(a:text, "\n")
      echomsg line
    endfor
	finally
    echohl None
	endtry
endfunction
