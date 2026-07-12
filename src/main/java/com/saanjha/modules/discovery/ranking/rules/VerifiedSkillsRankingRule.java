package com.saanjha.modules.discovery.ranking.rules;

import com.saanjha.modules.discovery.ranking.RankingContext;
import com.saanjha.modules.discovery.ranking.RankingRule;
import org.springframework.stereotype.Component;

/**
 * Rewards developers whose listed skills are actually verified (see
 * {@code UserSkill.isVerified}) over self-reported ones -- the direct
 * technical expression of the platform's "Meritocratic Verification"
 * principle (MES 0.4.2).
 */
@Component
public class VerifiedSkillsRankingRule implements RankingRule {

    @Override
    public String name() {
        return "verifiedSkills";
    }

    @Override
    public double score(RankingContext context) {
        return context.verifiedSkillRatio() == null ? 0 : context.verifiedSkillRatio();
    }
}
