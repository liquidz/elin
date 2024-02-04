function! elin#internal#plugin#search() abort
  return globpath(&runtimepath, 'elin/plugin.edn', 1, 1)
endfunction
