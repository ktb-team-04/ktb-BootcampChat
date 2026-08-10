package com.ktb.chatapp.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.Assert;

public class BannedWordChecker {

    private final List<TrieNode> automaton;
    
    public BannedWordChecker(Set<String> bannedWords) {
        Set<String> normalizedWords =
                bannedWords.stream()
                        .filter(word -> word != null && !word.isBlank())
                        .map(word -> word.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
        Assert.notEmpty(normalizedWords, "Banned words set must not be empty");
        this.automaton = buildAutomaton(normalizedWords);
    }
    
    public boolean containsBannedWord(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        int state = 0;

        for (int index = 0; index < normalizedMessage.length(); index++) {
            char character = normalizedMessage.charAt(index);

            while (state != 0 && !automaton.get(state).transitions.containsKey(character)) {
                state = automaton.get(state).failure;
            }

            state = automaton.get(state).transitions.getOrDefault(character, 0);
            if (automaton.get(state).terminal) {
                return true;
            }
        }

        return false;
    }

    /**
     * Aho-Corasick automaton. The dictionary is compiled once at application startup, so each
     * message is scanned once instead of checking all 10,000 banned words independently.
     */
    private List<TrieNode> buildAutomaton(Set<String> words) {
        List<TrieNode> nodes = new ArrayList<>();
        nodes.add(new TrieNode());

        for (String word : words) {
            int state = 0;
            for (int index = 0; index < word.length(); index++) {
                char character = word.charAt(index);
                Integer nextState = nodes.get(state).transitions.get(character);
                if (nextState == null) {
                    nextState = nodes.size();
                    nodes.get(state).transitions.put(character, nextState);
                    nodes.add(new TrieNode());
                }
                state = nextState;
            }
            nodes.get(state).terminal = true;
        }

        Queue<Integer> pending = new ArrayDeque<>();
        nodes.getFirst().transitions.values().forEach(pending::add);

        while (!pending.isEmpty()) {
            int currentState = pending.remove();
            TrieNode current = nodes.get(currentState);

            for (Map.Entry<Character, Integer> transition : current.transitions.entrySet()) {
                char character = transition.getKey();
                int nextState = transition.getValue();
                int failure = current.failure;

                while (failure != 0 && !nodes.get(failure).transitions.containsKey(character)) {
                    failure = nodes.get(failure).failure;
                }

                Integer failureTransition = nodes.get(failure).transitions.get(character);
                if (failureTransition != null && failureTransition != nextState) {
                    failure = failureTransition;
                }

                TrieNode next = nodes.get(nextState);
                next.failure = failure;
                next.terminal = next.terminal || nodes.get(failure).terminal;
                pending.add(nextState);
            }
        }

        return List.copyOf(nodes);
    }

    private static final class TrieNode {
        private final Map<Character, Integer> transitions = new HashMap<>();
        private int failure;
        private boolean terminal;
    }
}
