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

function! elin#internal#add_curpos_to_jumplist() abort
  " :h jumplist
  " > You can explicitly add a jump by setting the ' mark with "m'".
  silent normal! m'
endfunction

function! elin#internal#jump(path, lnum, col, jump_cmd) abort
  call elin#internal#add_curpos_to_jumplist()
  if expand('%:p') !=# a:path
    execute printf(':keepjumps %s %s', a:jump_cmd, a:path)
  endif
  call cursor(a:lnum, a:col)
  normal! zz
  return v:true
endfunction

function! elin#internal#eval(str) abort
  return eval(a:str)
endfunction

function! elin#internal#execute(cmd) abort
  call execute(a:cmd)
endfunction
