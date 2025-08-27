import React, { useCallback, useState } from "react";
import ForceGraph2D from "react-force-graph-2d";
import { InferredMapping} from "../../model/Mapping";

interface Node {
    id: string;
    label: string;
    type: string;
}

interface Link {
    source: string;
    target: string;
    label?: string;
}

interface GraphData {
    nodes: Node[];
    links: Link[];
}

function createMappingId(mapping: InferredMapping): string {
    const data = `${mapping.objectIri}|${mapping.predicateIri}|${mapping.subjectIri}|${mapping.mappingSetId}`;
    return data;
}

// Recursively build nodes and links from InferredMapping explanation
function buildGraphData(mapping: InferredMapping): GraphData {
    const nodes: Node[] = [];
    const links: Link[] = [];

    function traverse(current: InferredMapping) {
        const nodeId = createMappingId(current);
        // Create node for this mapping
        nodes.push({
            id: nodeId,
            label: `${current.subjectId ?? ""} ${current.predicateId ?? ""} ${current.objectId ?? ""}`,
            type: "mapping"
        });

        // If there are premises, create links from each premise to this node
        const premises = current.chainRuleApplications?.premises;
        if (premises && premises.length > 0) {
            premises.forEach(premise => {
                const premiseId = createMappingId(premise);
                // Recursively add premise nodes/links
                traverse(premise);
                links.push({
                    source: premiseId,
                    target: nodeId,
                    label: current.chainRuleApplications?.chainRule?.chainRuleAbbreviated
                });
            });
        }
    }

    traverse(mapping);

    return { nodes, links };
}




const InferredMappingForceGraph = ({ explanation }: { explanation: InferredMapping }) => {
    const graphData = buildGraphData(explanation);

    return (
        <div style={{ width: "100%", height: "400px" }}>
            <h1 className="section-subheading">Explanation of inferred mapping</h1>
            <ForceGraph2D
                graphData={graphData}
                nodeLabel="label"
                // nodeAutoColorBy="type"
                linkDirectionalArrowLength={6}
                linkDirectionalArrowRelPos={1}
                linkLabel="label"
                width={1000}
                height={400}
            />
        </div>
    );
};

export default InferredMappingForceGraph;
